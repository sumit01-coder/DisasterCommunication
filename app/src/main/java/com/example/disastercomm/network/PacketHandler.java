package com.example.disastercomm.network;

import android.util.Log;
import android.os.Build;
import com.example.disastercomm.data.AppDatabase;
import com.example.disastercomm.models.Message;
import com.example.disastercomm.utils.SecurityUtil;
import com.example.disastercomm.utils.MessageDebugHelper;
import com.example.disastercomm.utils.DeviceUtil;
import com.google.gson.Gson;
import android.content.Context;
import java.util.Map;
import java.security.PublicKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles all message processing with a dual-priority buffer system.
 * This implementation enforces Confidentiality, Integrity, and Availability (CIA).
 */
public class PacketHandler {
    private static final String TAG = "PacketHandler";
    private static final int MAX_CACHE_SIZE = 5000;

    private final MeshNetworkManager meshNetworkManager;
    private BluetoothConnectionManager bluetoothManager;
    private BLEHubClient bleHubClient;
    private final Gson gson;
    private final Set<String> seenMessageIds;
    private MessageListener messageListener;
    private final AppDatabase db;
    private final Context context;
    private final Map<String, PublicKey> peerPublicKeys = new java.util.concurrent.ConcurrentHashMap<>();
    private final OfflineMessageQueue offlineQueue;
    private final MessageBufferManager bufferManager; 
    private final Map<String, Long> rateLimitMap = new java.util.concurrent.ConcurrentHashMap<>();

    public interface MessageListener {
        void onMessageReceived(Message message);
    }

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public PacketHandler(Context context, MeshNetworkManager meshNetworkManager, AppDatabase db) {
        this.context = context;
        this.meshNetworkManager = meshNetworkManager;
        this.db = db;
        this.gson = new Gson();
        this.seenMessageIds = Collections.synchronizedSet(new java.util.LinkedHashSet<>());
        this.offlineQueue = new OfflineMessageQueue(context);
        this.bufferManager = new MessageBufferManager();

        startBufferProcessors();

        executor.execute(() -> {
            try {
                SecurityUtil.getOrGenerateKeyPair(context);
            } catch (Exception e) {
                Log.e(TAG, "Key Init Failed", e);
            }
        });
    }

    public PacketHandler(Context context, MeshNetworkManager meshNetworkManager) {
        this(context, meshNetworkManager, null);
    }

    private void startBufferProcessors() {
        // 1. Inbound Buffer Processor (Integrity & Delivery)
        executor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Message msg = bufferManager.takeInbound();
                    processIncomingMessageInternal(msg);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Inbound buffer error", e);
                }
            }
        });

        // 2. Outbound Buffer Processor (Availability & Encryption)
        executor.execute(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Message msg = bufferManager.takeOutbound();
                    processOutgoingMessageInternal(msg);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "Outbound buffer error", e);
                }
            }
        });
    }

    public void handlePayload(String fromEndpointId, byte[] payload) {
        String json = new String(payload, StandardCharsets.UTF_8);
        try {
            Message message = gson.fromJson(json, Message.class);
            if (message != null) {
                // Instantly buffer to prevent transport thread blocking
                bufferManager.bufferInbound(message);
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON Parse Error", e);
        }
    }

    private void processIncomingMessageInternal(Message message) {
        // 1. Deduplication (Integrity)
        if (seenMessageIds.contains(message.id)) return;
        seenMessageIds.add(message.id);
        if (seenMessageIds.size() > MAX_CACHE_SIZE) seenMessageIds.clear();

        // 1.5 Rate Limiting (Congestion Mitigation)
        long lastTime = rateLimitMap.getOrDefault(message.senderId, 0L);
        long now = System.currentTimeMillis();
        if (now - lastTime < 500 && message.type != Message.Type.SOS && message.type != Message.Type.KEY_EXCHANGE) { 
            Log.w(TAG, "Rate limit exceeded for " + message.senderId);
            return;
        }
        rateLimitMap.put(message.senderId, now);

        // 2. Anti-Replay (Integrity)
        if (Math.abs(System.currentTimeMillis() - message.timestamp) > 600000) return;

        // 2.5 Anti-Spoofing & Web of Trust (Integrity)
        if (message.signature != null && !message.signature.startsWith("SIG_")) {
            PublicKey senderKey = peerPublicKeys.get(message.senderId);
            if (senderKey != null) {
                String dataToVerify = message.id + message.senderId + message.content + message.nonce;
                // Try ECDSA verification first (Fast/Battery Efficient)
                boolean verified = SecurityUtil.verifySignatureEcdsa(dataToVerify, message.signature, senderKey);
                // Fallback to RSA if ECDSA fails (backward compatibility)
                if (!verified) {
                    verified = SecurityUtil.verifySignature(dataToVerify, message.signature, senderKey);
                }
                
                if (!verified) {
                    Log.w(TAG, "Spoofing attempt detected! Signature mismatch from " + message.senderId);
                    return; // Drop packet
                }
            }
        }

        // 2.7 Routing Loop Mitigation
        String myId = DeviceUtil.getDeviceId(context);
        if (message.routePath != null && message.routePath.contains(myId) && !message.senderId.equals(myId)) {
            Log.w(TAG, "♻️ Routing Loop detected! Packet already traversed this node. Dropping.");
            return;
        }

        // 3. Handle Key Exchange
        if (message.type == Message.Type.KEY_EXCHANGE) {
            handleKeyExchange(message);
            return;
        }

        // String myId = DeviceUtil.getDeviceId(context); // Already defined above
        boolean isForMe = "ALL".equals(message.receiverId) || myId.equals(message.receiverId);

        if (isForMe) {
            deliverToUser(message);
        }

        // 4. Relay / Store-and-Forward (Availability)
        if (message.ttl > 0 && (!isForMe || "ALL".equals(message.receiverId))) {
            // Append self to route path
            message.routePath = message.routePath == null ? myId : message.routePath + "," + myId;
            
            // Dynamic TTL: Reduce TTL faster in dense networks if we had neighbor count, 
            // for now reduce normally to prevent infinite loops.
            message.ttl--;
            bufferManager.bufferOutbound(message);
        }
    }

    private void deliverToUser(Message message) {
        String decryptedContent = message.content;
        // E2EE Decryption (Confidentiality)
        if (message.encryptedAesKey != null) {
            decryptedContent = decryptMessage(message);
        }

        Message delivered = gson.fromJson(gson.toJson(message), Message.class);
        delivered.content = decryptedContent;

        // Save to Database (Availability/History)
        if (db != null) {
            db.messageDao().insertMessage(delivered);
            if (delivered.type == Message.Type.TEXT) updateContacts(delivered);
        }

        if (delivered.type == Message.Type.LOCATION_UPDATE) updatePeerLocation(delivered);

        // Notify UI
        if (messageListener != null) {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                messageListener.onMessageReceived(delivered));
        }
    }

    private void processOutgoingMessageInternal(Message toSend) {
        // 1. Encryption (Confidentiality)
        if (!"ALL".equals(toSend.receiverId) && toSend.type == Message.Type.TEXT && !toSend.isEncrypted) {
            encryptOutgoingMessage(toSend);
        }

        // 2. Signing & Security (Integrity)
        if (toSend.nonce == null) toSend.nonce = UUID.randomUUID().toString();
        
        try {
            // Use advanced ECDSA for signing (much faster, saves battery compared to RSA)
            java.security.KeyPair ecdsaKp = SecurityUtil.getOrGenerateEcdsaKeyPair(context);
            if (ecdsaKp != null && ecdsaKp.getPrivate() != null) {
                String dataToSign = toSend.id + toSend.senderId + toSend.content + toSend.nonce;
                toSend.signature = SecurityUtil.signDataEcdsa(dataToSign, ecdsaKp.getPrivate());
            } else {
                toSend.signature = "SIG_" + (toSend.id + toSend.nonce).hashCode();
            }
        } catch (Exception e) {
            Log.e(TAG, "ECDSA Signing Failed", e);
            toSend.signature = "SIG_" + (toSend.id + toSend.nonce).hashCode();
        }
        
        toSend.tokenExpiry = System.currentTimeMillis() + 300000;

        // 3. Physical Transport selection (Availability)
        executePhysicalSend(toSend);
    }

    public void sendMessage(Message message) {
        if (db != null && (message.type == Message.Type.TEXT || message.type == Message.Type.SOS)) {
            db.messageDao().insertMessage(message);
        }
        bufferManager.bufferOutbound(message);
    }

    private void executePhysicalSend(Message message) {
        String json = gson.toJson(message);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        // Multi-Path availability: Broadcast across all active transports
        if (meshNetworkManager != null) meshNetworkManager.broadcastPayload(bytes);
        if (bluetoothManager != null) bluetoothManager.broadcastData(bytes, null);
        if (bleHubClient != null && bleHubClient.isConnected()) bleHubClient.sendData(json);
        if (localTcpManager != null) localTcpManager.broadcastData(bytes, null);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && wifiAwareNetworkManager != null) {
            wifiAwareNetworkManager.broadcastMessage(bytes);
        }
    }

    private void encryptOutgoingMessage(Message msg) {
        PublicKey peerKey = peerPublicKeys.get(msg.receiverId);
        if (peerKey != null) {
            try {
                javax.crypto.SecretKey aesKey = SecurityUtil.generateAesKey();
                msg.content = SecurityUtil.encryptAes(msg.content, aesKey);
                msg.encryptedAesKey = SecurityUtil.encryptRsa(aesKey, peerKey);
                msg.isEncrypted = true;
            } catch (Exception e) { Log.e(TAG, "Encryption failed", e); }
        }
    }

    private String decryptMessage(Message msg) {
        try {
            java.security.KeyPair kp = SecurityUtil.getOrGenerateKeyPair(context);
            if (kp == null || kp.getPrivate() == null) return "[Error: Keys missing]";
            javax.crypto.SecretKey key = SecurityUtil.decryptRsa(msg.encryptedAesKey, kp.getPrivate());
            return SecurityUtil.decryptAes(msg.content, key);
        } catch (Exception e) { return "[Decryption Failed]"; }
    }

    private void handleKeyExchange(Message msg) {
        PublicKey pk = SecurityUtil.decodePublicKey(msg.publicKey);
        if (pk == null) {
            // Try decoding as ECDSA
            pk = SecurityUtil.decodeEcdsaPublicKey(msg.publicKey);
        }
        
        if (pk != null) {
            PublicKey existingKey = peerPublicKeys.get(msg.senderId);
            if (existingKey != null && !existingKey.equals(pk)) {
                Log.w(TAG, "🚨 MITM ATTACK DETECTED! Public key for " + msg.senderId + " changed! Rejecting new key.");
            } else {
                if (existingKey == null) {
                    Log.d(TAG, "Pinned Public Key (Web of Trust) for " + msg.senderId);
                }
                peerPublicKeys.put(msg.senderId, pk);
            }
        }
        if (msg.ttl > 0) {
            msg.ttl--;
            bufferManager.bufferOutbound(msg);
        }
    }

    private void updateContacts(Message msg) {
        com.example.disastercomm.models.User user = new com.example.disastercomm.models.User(msg.senderId, msg.senderName);
        user.lastMessagePreview = msg.content;
        user.lastMessageTimestamp = msg.timestamp;
        db.userDao().insertUser(user);
    }

    private void updatePeerLocation(Message msg) {
        try {
            String[] parts = msg.content.split(",");
            if (parts.length == 2) {
                com.example.disastercomm.PeerLocationManager.getInstance().updatePeerLocation(
                    msg.senderId, Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), 
                    msg.isLiveSharing, msg.sharingUntil);
            }
        } catch (Exception e) { Log.e(TAG, "Location parse error", e); }
    }

    // Setters & Lifecycle
    private WifiAwareNetworkManager wifiAwareNetworkManager;
    private LocalTcpManager localTcpManager;
    public void setBluetoothManager(BluetoothConnectionManager b) { this.bluetoothManager = b; }
    public void setBleHubClient(BLEHubClient h) { this.bleHubClient = h; }
    public void setLocalTcpManager(LocalTcpManager l) { this.localTcpManager = l; }
    public void setWifiAwareNetworkManager(WifiAwareNetworkManager w) { this.wifiAwareNetworkManager = w; }
    public void setMessageListener(MessageListener l) { this.messageListener = l; }

    public void retryOfflineMessages() {
        executor.execute(() -> {
            for (Message m : offlineQueue.getPendingMessages()) {
                bufferManager.bufferOutbound(m);
                offlineQueue.remove(m);
            }
        });
    }

    public void close() { executor.shutdown(); }

    public void broadcastPublicKey(String username) {
        executor.execute(() -> {
            try {
                // Broadcast the ECDSA Web of Trust Public Key instead of heavy RSA
                java.security.KeyPair kp = SecurityUtil.getOrGenerateEcdsaKeyPair(context);
                if (kp != null && kp.getPublic() != null) {
                    String pubKeyStr = android.util.Base64.encodeToString(kp.getPublic().getEncoded(), android.util.Base64.DEFAULT);
                    Message msg = new Message(DeviceUtil.getDeviceId(context), username, Message.Type.KEY_EXCHANGE, "KEY_EXCHANGE");
                    msg.publicKey = pubKeyStr;
                    msg.receiverId = "ALL";
                    sendMessage(msg);
                    Log.d(TAG, "📡 Public Key Broadcasted for: " + username);
                }
            } catch (Exception e) {
                Log.e(TAG, "Key Broadcast Failed", e);
            }
        });
    }

    public boolean hasPublicKey(String userId) {
        return userId != null && peerPublicKeys.containsKey(userId);
    }
}
