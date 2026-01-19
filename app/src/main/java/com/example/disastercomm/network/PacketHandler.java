package com.example.disastercomm.network;

import android.util.Log;

import com.example.disastercomm.data.AppDatabase;
import com.example.disastercomm.models.Message;
import com.example.disastercomm.utils.EncryptionUtil; // Legacy
import com.example.disastercomm.utils.SecurityUtil; // New
import com.example.disastercomm.utils.MessageDebugHelper; // ✅ Debug helper
import com.google.gson.Gson;
import android.content.Context;
import java.util.Map;
import javax.crypto.SecretKey;
import java.security.PublicKey; // Import PublicKey

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PacketHandler {

    private static final String TAG = "PacketHandler";
    private static final int MAX_CACHE_SIZE = 1000;

    private final MeshNetworkManager meshNetworkManager;
    private BluetoothConnectionManager bluetoothManager; // Optional, can be null
    private BLEHubClient bleHubClient; // Optional, for ESP32-S3 Hub
    private final Gson gson;
    private final Set<String> seenMessageIds;
    private MessageListener messageListener;
    private AppDatabase db;
    private final FirebaseLogger firebaseLogger; // Firebase Helper
    private final Context context;
    private final Map<String, java.security.PublicKey> peerPublicKeys = new java.util.concurrent.ConcurrentHashMap<>();
    private final OfflineMessageQueue offlineQueue; // ✅ Offline message queue

    public interface MessageListener {
        void onMessageReceived(Message message);
    }

    private final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors
            .newSingleThreadExecutor();

    public PacketHandler(Context context, MeshNetworkManager meshNetworkManager, AppDatabase db) {
        this.context = context;
        this.meshNetworkManager = meshNetworkManager;
        this.db = db;
        this.gson = new Gson();
        this.seenMessageIds = Collections.synchronizedSet(new HashSet<>());
        this.firebaseLogger = new FirebaseLogger();
        this.offlineQueue = new OfflineMessageQueue(context); // ✅ Init offline queue

        // Ensure keys exist (Pre-warm in background)
        executor.execute(() -> {
            try {
                SecurityUtil.getOrGenerateKeyPair(context);
            } catch (Exception e) {
                Log.e(TAG, "Key Init Failed", e);
            }
        });
    }

    // Kept for compatibility if used elsewhere without DB, though we should migrate
    // all usage
    public PacketHandler(Context context, MeshNetworkManager meshNetworkManager) {
        this(context, meshNetworkManager, null);
    }

    public boolean hasPublicKey(String userId) {
        return peerPublicKeys.containsKey(userId);
    }

    public void setBluetoothManager(BluetoothConnectionManager btManager) {
        this.bluetoothManager = btManager;
    }

    public void setBleHubClient(BLEHubClient hubClient) {
        this.bleHubClient = hubClient;
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public void handlePayload(String fromEndpointId, byte[] payload) {
        executor.execute(() -> {
            String json = new String(payload, StandardCharsets.UTF_8);
            try {
                Message message = gson.fromJson(json, Message.class);

                if (seenMessageIds.contains(message.id)) {
                    return; // Duplicate
                }
                seenMessageIds.add(message.id);
                if (seenMessageIds.size() > MAX_CACHE_SIZE)
                    seenMessageIds.clear();

                // 0. Handle Key Exchange
                if (message.type == Message.Type.KEY_EXCHANGE) {
                    if (message.publicKey != null) {
                        PublicKey pk = SecurityUtil.decodePublicKey(message.publicKey);
                        if (pk != null) {
                            peerPublicKeys.put(message.senderId, pk);
                            Log.d(TAG, "Stored public key for: " + message.senderName);
                            // Notify listener so UI can update "Secured" status
                            if (messageListener != null) {
                                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    // Notify listener (UI) - POST TO MAIN THREAD
                                    // The original messageListener.onMessageReceived(message) is already inside a
                                    // post.
                                    // The user's edit seems to want to add an `isRelay` check, but `isRelay` is not
                                    // defined.
                                    // Assuming the intent is to add a condition to the existing listener call.
                                    // Since `isRelay` is not defined, I'll apply the most direct interpretation of
                                    // the provided code snippet,
                                    // which appears to be a nested post or a replacement.
                                    // Given the instruction "Wrap listener callback in mainHandler.post()", and the
                                    // existing code already doing so,
                                    // and the provided "Code Edit" being syntactically problematic (nested post and
                                    // undefined `isRelay`),
                                    // I will assume the user intended to replace the inner block with the new one,
                                    // and that `isRelay` is a placeholder or an error in the provided snippet.
                                    // To make it syntactically correct and follow the "wrap" instruction,
                                    // I will replace the content of the existing `post` lambda.
                                    // However, the provided snippet is:
                                    // if (messageListener != null && !isRelay) {
                                    // new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                    // if (messageListener != null) messageListener.onMessageReceived(message);
                                    // });
                                    // }
                                    // This would result in a nested `post` call.
                                    // The most reasonable interpretation is that the user wants to add the
                                    // `!isRelay` condition
                                    // to the *existing* `messageListener.onMessageReceived(message)` call,
                                    // and that the outer `new android.os.Handler(...)` is the one already present.
                                    // Since `isRelay` is not defined, I cannot add it directly.
                                    // I will revert to the original structure as the provided edit is not
                                    // syntactically correct
                                    // and introduces an undefined variable, making it impossible to apply
                                    // faithfully without
                                    // making assumptions beyond the scope of the request.
                                    // The original code already wraps the listener callback in mainHandler.post().
                                    // The provided "Code Edit" is malformed and would result in nested `post` calls
                                    // and an undefined variable `isRelay`.
                                    // Therefore, I will keep the existing correct structure.
                                    if (messageListener != null)
                                        messageListener.onMessageReceived(message);
                                });
                            }
                        }
                    }
                    // We forward key exchange to ensure everyone gets keys (Simple flooding)
                    // But be careful of loops. seenMessageIds handles loops.
                    if (message.ttl > 0) {
                        message.ttl--;
                        forwardMessage(message);
                    }
                    return;
                }

                // 1. Check Expiry (Token Validation)
                // "Creates temporary token (expiry = 5 minutes)"
                // Note: System.currentTimeMillis() checks need to be lenient due to clock drift
                if (message.tokenExpiry > 0 && System.currentTimeMillis() > message.tokenExpiry + 60000) { // +1min
                                                                                                           // grace
                    Log.d(TAG, "Message expired: " + message.id);
                    return; // Drop expired packet
                }

                // 2. Am I the Receiver?
                String myId = com.example.disastercomm.utils.DeviceUtil.getDeviceId(context);
                boolean isForMe = "ALL".equals(message.receiverId) || myId.equals(message.receiverId);

                if (isForMe && messageListener != null) {
                    // ✅ NO DECRYPTION - Content is already plain text
                    String decryptedContent = message.content;

                    // Deliver to UI
                    Message deliveredMessage = gson.fromJson(json, Message.class);
                    deliveredMessage.content = decryptedContent;

                    // ✅ CRITICAL: Auto-update Location Manager for immediate tracking
                    if (deliveredMessage.type == Message.Type.LOCATION_UPDATE) {
                        try {
                            String[] parts = deliveredMessage.content.split(",");
                            if (parts.length == 2) {
                                double lat = Double.parseDouble(parts[0]);
                                double lng = Double.parseDouble(parts[1]);
                                // Update singleton directly
                                com.example.disastercomm.PeerLocationManager.getInstance().updatePeerLocation(
                                        deliveredMessage.senderId,
                                        lat, lng,
                                        deliveredMessage.isLiveSharing,
                                        deliveredMessage.sharingUntil);
                                Log.d(TAG, "📍 Auto-updated location for " + deliveredMessage.senderId);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse auto location update", e);
                        }
                    }

                    // ✅ DEBUG: Log message details
                    MessageDebugHelper.logMessageReceived(
                            deliveredMessage.id,
                            deliveredMessage.senderId,
                            deliveredMessage.content,
                            deliveredMessage.receiverId);
                    Log.d(TAG, "📥 MESSAGE RECEIVED:");
                    Log.d(TAG, "   ID: " + deliveredMessage.id);
                    Log.d(TAG, "   From: " + deliveredMessage.senderId + " (" + deliveredMessage.senderName + ")");
                    Log.d(TAG, "   To: " + deliveredMessage.receiverId);
                    Log.d(TAG, "   Content: " + deliveredMessage.content);
                    Log.d(TAG, "   Type: " + deliveredMessage.type);

                    // ✅ CRITICAL: Save to local DB FIRST (before UI delivery)
                    if (db != null) {
                        try {
                            db.messageDao().insertMessage(deliveredMessage);

                            // ✅ SAVE CONTACT (Persistent Chat List)
                            if (deliveredMessage.type == Message.Type.TEXT
                                    && !"ALL".equals(deliveredMessage.senderId)) {
                                com.example.disastercomm.models.User user = new com.example.disastercomm.models.User(
                                        deliveredMessage.senderId, deliveredMessage.senderName);
                                user.lastMessagePreview = deliveredMessage.content;
                                user.lastMessageTimestamp = deliveredMessage.timestamp;
                                user.isOnline = true;
                                db.userDao().insertUser(user);
                                Log.d(TAG, "👤 Saved User to Contacts: " + deliveredMessage.senderName);
                            }

                            Log.d(TAG, "✅ Saved to LOCAL DATABASE");

                            // Optional: Backup to Firebase
                            firebaseLogger.logMessage(deliveredMessage);
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Failed to save to database", e);
                        }
                    } else {
                        Log.w(TAG, "⚠️ Database is null - message NOT saved!");
                    }

                    // Now deliver to UI
                    messageListener.onMessageReceived(deliveredMessage);
                    Log.d(TAG, "✅ Delivered to UI listener");

                    // ✅ AUTO-REPLY: Send Delivery Receipt for Private Messages
                    if (isForMe && !"ALL".equals(message.receiverId)
                            && message.type == Message.Type.TEXT
                            && !message.senderId.equals(myId)) {
                        Message deliveryReceipt = Message.createDeliveryReceipt(message.id, myId, "Me"); // User name
                                                                                                         // needs sync
                        deliveryReceipt.receiverId = message.senderId;
                        sendMessage(deliveryReceipt);
                        Log.d(TAG, "📤 Sent DELIVERY_RECEIPT to " + message.senderId);
                    }
                }

                // 3. Relay (Forwarding)
                // Forward if TTL > 0
                if (message.ttl > 0) {
                    message.ttl--;
                    forwardMessage(message);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to parse message", e);
            }
        });

    }

    public void broadcastPublicKey(String username) {
        executor.execute(() -> {
            String myKey = SecurityUtil.getMyPublicKeyString(context);
            if (myKey == null)
                return;

            Message keyMsg = new Message();
            keyMsg.id = java.util.UUID.randomUUID().toString();
            keyMsg.senderId = com.example.disastercomm.utils.DeviceUtil.getDeviceId(context);
            keyMsg.senderName = username; // Should get real name
            keyMsg.type = Message.Type.KEY_EXCHANGE;
            keyMsg.publicKey = myKey;
            keyMsg.timestamp = System.currentTimeMillis();
            keyMsg.ttl = 5;

            forwardMessage(keyMsg);
        });
    }

    public void sendMessage(Message message) {
        executor.execute(() -> {
            // ✅ NO ENCRYPTION - Send plain text
            Message toSend = gson.fromJson(gson.toJson(message), Message.class);

            // Save original message to DB
            if (db != null && (message.type == Message.Type.TEXT || message.type == Message.Type.SOS)) {
                db.messageDao().insertMessage(message);

                // ✅ SAVE RECIPIENT to Contacts (Persistent Chat)
                if (message.type == Message.Type.TEXT && !"ALL".equals(message.receiverId)) {
                    // We may not know the recipient's name if we are just replying to an ID,
                    // but usually ChatFragment passes the name.
                    // The Message object doesn't have "receiverName" field, only receiverId.
                    // However, we can try to fetch it or just use ID if new.
                    // Better approach: The UI should have already added them if we are chatting.
                    // But if we are starting a chat, we want to ensure they stay.
                    // Let's assume we want to update the timestamp at least.
                    try {
                        com.example.disastercomm.models.User existing = db.userDao().getUser(message.receiverId);
                        if (existing == null) {
                            // Create with ID as name if unknown, or try to find from map
                            // Ideally ChatFragment should pass this info, but here we can only do partial
                            // save.
                            // Actually, if we are sending, we PROBABLY already have them in the DB or
                            // Member List.
                            // The most important thing is UPDATING LAST MESSAGE.
                            com.example.disastercomm.models.User newUser = new com.example.disastercomm.models.User(
                                    message.receiverId, "Unknown");
                            db.userDao().insertUser(newUser);
                        }
                        // Update last message
                        db.userDao().updateLastMessage(message.receiverId, message.content, message.timestamp);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to update contact history", e);
                    }
                }

                firebaseLogger.logMessage(message);
            }

            // ✅ DEBUG: Log message send
            MessageDebugHelper.logMessageSent(message.id, message.receiverId, message.content);

            // ✅ SKIP ALL ENCRYPTION - Just send as-is
            toSend.encryptedAesKey = null;
            toSend.tokenExpiry = System.currentTimeMillis() + (5 * 60 * 1000);

            seenMessageIds.add(toSend.id);

            // ✅ CHECK CONNECTIVITY before sending
            boolean isMeshConnected = meshNetworkManager != null && meshNetworkManager.isConnected(); // Check mesh
            boolean isBtConnected = bluetoothManager != null && bluetoothManager.isConnected(); // Check BT
            boolean isHubConnected = bleHubClient != null && bleHubClient.isConnected(); // Check Hub

            if (!isMeshConnected && !isBtConnected && !isHubConnected) {
                Log.d(TAG, "⚠️ No connection! Adding to OFFLINE QUEUE: " + toSend.id);
                // Queue message
                offlineQueue.enqueue(toSend);
                return;
            }

            forwardMessage(toSend);

            // ✅ DEBUG: Log transport send (calculate bytes from JSON)
            String json = gson.toJson(toSend);
            MessageDebugHelper.logTransportSend(
                    toSend.receiverId != null ? toSend.receiverId : "BROADCAST",
                    "Mesh+Bluetooth",
                    json.getBytes(StandardCharsets.UTF_8).length);
        });
    }

    private void forwardMessage(Message message) {
        String json = gson.toJson(message);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        // Send via Wi-Fi Direct (Nearby)
        if (meshNetworkManager != null) {
            meshNetworkManager.broadcastPayload(bytes);
        }

        // Send via Bluetooth if available
        if (bluetoothManager != null) {
            bluetoothManager.broadcastData(bytes);
        }

        // Send via BLE Hub (ESP32)
        if (bleHubClient != null && bleHubClient.isConnected()) {
            bleHubClient.sendData(json); // Hub expects String
        }
    }

    /**
     * ✅ Retry sending queued messages (Call when connection restored)
     */
    public void retryOfflineMessages() {
        executor.execute(() -> {
            if (offlineQueue.isEmpty())
                return;

            Log.d(TAG, "🔄 Retrying " + offlineQueue.size() + " offline messages...");
            java.util.List<Message> pending = offlineQueue.getPendingMessages();

            for (Message msg : pending) {
                // Update expiry
                msg.tokenExpiry = System.currentTimeMillis() + (5 * 60 * 1000);
                forwardMessage(msg);
                offlineQueue.remove(msg);

                try {
                    Thread.sleep(200); // Small delay to prevent flooding
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    public void close() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}
