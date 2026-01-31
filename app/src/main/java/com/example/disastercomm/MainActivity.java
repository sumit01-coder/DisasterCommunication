package com.example.disastercomm;

import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.disastercomm.models.Message;
import com.example.disastercomm.network.MeshNetworkManager;
import com.example.disastercomm.network.NetworkStateMonitor;
import com.example.disastercomm.network.PacketHandler;
import com.example.disastercomm.data.AppDatabase;
import com.example.disastercomm.utils.DeviceUtil;
import com.example.disastercomm.utils.NotificationSoundManager;
import com.example.disastercomm.utils.PermissionsManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity
        implements MeshNetworkManager.MeshCallback, PacketHandler.MessageListener,
        com.example.disastercomm.ChatAdapter.OnLocationClickListener {

    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    private TextView tvStatus;
    private android.view.View viewScanRipple;
    private RecyclerView rvPeers;
    private PeersAdapter peersAdapter;
    private Button btnSos, btnChat;

    private MeshNetworkManager meshNetworkManager;
    private PacketHandler packetHandler;

    private com.example.disastercomm.utils.LocationHelper locationHelper;
    private com.example.disastercomm.utils.BluetoothScanner bluetoothScanner;
    private com.example.disastercomm.network.BluetoothConnectionManager bluetoothConnectionManager;
    private com.example.disastercomm.network.BLEAdvertiser bleAdvertiser;
    private com.example.disastercomm.network.ConnectionPoolManager connectionPoolManager;
    private NetworkStateMonitor networkStateMonitor;
    private NotificationSoundManager notificationSoundManager;
    private final Map<String, String> bluetoothDeviceMap = new HashMap<>(); // address -> name
    private final Map<String, String> meshDeviceMap = new HashMap<>(); // endpointId -> name
    private final List<String> activeNetworks = new ArrayList<>();

    private android.animation.ObjectAnimator scaleXAnim, scaleYAnim, alphaAnim;
    private android.animation.AnimatorSet pulseAnimatorSet;

    private String username;
    private final List<String> bluetoothDevices = new ArrayList<>();

    // Set to avoid spamming notifications for the same session
    private final java.util.Set<String> notifiedLiveSharers = new java.util.HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load Username
        android.content.SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        username = prefs.getString("username", "Unknown");

        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        viewScanRipple = findViewById(R.id.viewScanRipple);
        rvPeers = findViewById(R.id.rvPeers);
        btnSos = findViewById(R.id.btnSos);
        btnChat = findViewById(R.id.btnChat);
        Button btnMap = findViewById(R.id.btnMap);

        peersAdapter = new PeersAdapter();
        rvPeers.setLayoutManager(new LinearLayoutManager(this));
        rvPeers.setAdapter(peersAdapter);

        // Initialize Network State Monitor
        initNetworkMonitor();

        // Initialize Notification Sound Manager
        notificationSoundManager = new NotificationSoundManager(this);

        locationHelper = new com.example.disastercomm.utils.LocationHelper(this);
        if (PermissionsManager.hasPermissions(this)) {
            try {
                locationHelper.getCurrentLocation((lat, lng) -> {
                    getSupportActionBar().setSubtitle(String.format("GPS: %.4f, %.4f", lat, lng));
                });
            } catch (Exception e) {
            }
        }

        // Initialize BT Scanner
        bluetoothScanner = new com.example.disastercomm.utils.BluetoothScanner(this, (name, address) -> {
            runOnUiThread(() -> {
                if (!bluetoothDevices.contains(name)) {
                    bluetoothDevices.add(name);
                    bluetoothDeviceMap.put(address, name);
                    refreshDeviceList();

                    // Auto-connect to discovered devices
                    if (bluetoothConnectionManager != null) {
                        android.bluetooth.BluetoothDevice device = android.bluetooth.BluetoothAdapter
                                .getDefaultAdapter().getRemoteDevice(address);
                        bluetoothConnectionManager.connectToDevice(device);
                    }
                }
            });
        });

        if (!PermissionsManager.hasPermissions(this)) {
            PermissionsManager.requestPermissions(this, REQUEST_CODE_REQUIRED_PERMISSIONS);
            tvStatus.setText("Tap here to grant permissions");
        } else {
            initMeshNetwork();
            bluetoothScanner.startScan();
        }

        tvStatus.setOnClickListener(v -> {
            if (!PermissionsManager.hasPermissions(this)) {
                requestPermissions();
            } else {
                Toast.makeText(this, "Permissions already granted. Mesh Active.", Toast.LENGTH_SHORT).show();
                if (packetHandler == null)
                    initMeshNetwork();
                bluetoothScanner.startScan();
            }
        });

        btnSos.setOnClickListener(v -> sendSos());
        btnChat.setOnClickListener(v -> showChatDialog());
        btnMap.setOnClickListener(v -> startActivity(new android.content.Intent(this, MapActivity.class)));

        // Update Title with Greeting
        getSupportActionBar().setTitle("D-Comm: " + username);
    }

    private void initNetworkMonitor() {
        networkStateMonitor = new NetworkStateMonitor(this, new NetworkStateMonitor.NetworkStateListener() {
            @Override
            public void onNetworkAvailable(int networkType, String networkName) {
                runOnUiThread(() -> {
                    if (!activeNetworks.contains(networkName)) {
                        activeNetworks.add(networkName);
                        updateNetworkStatus();
                        Toast.makeText(MainActivity.this, networkName + " connected", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onNetworkLost(int networkType) {
                runOnUiThread(() -> updateNetworkStatus());
            }

            @Override
            public void onInternetAvailable() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Internet connectivity available", Toast.LENGTH_SHORT).show();
                    updateNetworkStatus();
                });
            }

            @Override
            public void onInternetLost() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Internet lost - using mesh only", Toast.LENGTH_SHORT).show();
                    updateNetworkStatus();
                });
            }
        });
        networkStateMonitor.startMonitoring();
    }

    private void updateNetworkStatus() {
        StringBuilder status = new StringBuilder();

        if (networkStateMonitor != null && networkStateMonitor.isConnectedToInternet()) {
            status.append("🌐 Internet | ");
        }

        int meshCount = (meshNetworkManager != null) ? meshNetworkManager.getConnectedEndpoints().size() : 0;
        int btCount = bluetoothDevices.size();

        if (meshCount > 0) {
            status.append("📶 Mesh: ").append(meshCount).append(" | ");
        }
        if (btCount > 0) {
            status.append("🔵 BT: ").append(btCount).append(" | ");
        }

        if (status.length() == 0) {
            status.append("Searching for connections...");
        } else {
            status.setLength(status.length() - 3); // Remove trailing " | "
        }

        tvStatus.setText(status.toString());
    }

    private void refreshDeviceList() {
        List<com.example.disastercomm.models.PeerItem> mergedList = new ArrayList<>();

        // Add Bluetooth Section
        if (!bluetoothDevices.isEmpty()) {
            mergedList.add(new com.example.disastercomm.models.PeerItem("HDR_BT", "🔵 Bluetooth Devices",
                    com.example.disastercomm.models.PeerItem.Type.HEADER));
            for (String name : bluetoothDevices) {
                mergedList.add(new com.example.disastercomm.models.PeerItem(name, name,
                        com.example.disastercomm.models.PeerItem.Type.BLUETOOTH));
            }
        }

        // Add Mesh Section
        List<String> meshPeers = (meshNetworkManager != null) ? meshNetworkManager.getConnectedEndpoints()
                : new ArrayList<>();
        if (!meshPeers.isEmpty()) {
            mergedList.add(new com.example.disastercomm.models.PeerItem("HDR_MESH", "📶 Nearby (Wi-Fi Direct)",
                    com.example.disastercomm.models.PeerItem.Type.HEADER));
            for (String peerId : meshPeers) {
                String name = meshDeviceMap.getOrDefault(peerId,
                        "Peer: " + peerId.substring(0, Math.min(4, peerId.length())));
                mergedList.add(new com.example.disastercomm.models.PeerItem(peerId, name,
                        com.example.disastercomm.models.PeerItem.Type.MESH));
            }
        }

        peersAdapter.updateList(mergedList);
        updateNetworkStatus();
    }

    private void initMeshNetwork() {
        // Initialize Connection Pool Manager first
        connectionPoolManager = new com.example.disastercomm.network.ConnectionPoolManager();

        meshNetworkManager = new MeshNetworkManager(this, username, this);
        meshNetworkManager.setConnectionPoolManager(connectionPoolManager);

        packetHandler = new PacketHandler(this, meshNetworkManager, AppDatabase.getDatabase(this));
        packetHandler.setMessageListener(this);

        // Initialize Bluetooth Connection Manager
        bluetoothConnectionManager = new com.example.disastercomm.network.BluetoothConnectionManager(this,
                new com.example.disastercomm.network.BluetoothConnectionManager.BluetoothCallback() {
                    @Override
                    public void onBluetoothConnected(String address, String deviceName) {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "BT Connected: " + deviceName, Toast.LENGTH_SHORT).show();
                            refreshDeviceList();
                        });
                    }

                    @Override
                    public void onBluetoothDisconnected(String address) {
                        runOnUiThread(() -> {
                            bluetoothDevices.remove(bluetoothDeviceMap.get(address));
                            bluetoothDeviceMap.remove(address);
                            refreshDeviceList();
                        });
                    }

                    @Override
                    public void onBluetoothDataReceived(String address, byte[] data) {
                        packetHandler.handlePayload(address, data);
                    }
                });
        bluetoothConnectionManager.setConnectionPoolManager(connectionPoolManager);
        bluetoothConnectionManager.start();
        packetHandler.setBluetoothManager(bluetoothConnectionManager);

        // Initialize BLE Advertiser for fast discovery
        bleAdvertiser = new com.example.disastercomm.network.BLEAdvertiser(
                this, username, DeviceUtil.getDeviceId(this),
                new com.example.disastercomm.network.BLEAdvertiser.BLECallback() {
                    @Override
                    public void onBLEDeviceFound(String address, String name, int rssi) {
                        runOnUiThread(() -> {
                            if (!bluetoothDevices.contains(name)) {
                                bluetoothDevices.add(name);
                                bluetoothDeviceMap.put(address, name);
                                refreshDeviceList();

                                // Auto-connect via classic Bluetooth
                                if (bluetoothConnectionManager != null) {
                                    android.bluetooth.BluetoothDevice device = android.bluetooth.BluetoothAdapter
                                            .getDefaultAdapter().getRemoteDevice(address);
                                    bluetoothConnectionManager.connectToDevice(device);
                                }

                                // Update pool with signal strength
                                if (connectionPoolManager != null) {
                                    connectionPoolManager.updateRSSI(address, rssi);
                                }

                                Log.d("MainActivity", "BLE device found: " + name + " (RSSI: " + rssi + "dBm)");
                            }
                        });
                    }

                    @Override
                    public void onBLEConnectionStateChanged(String address, boolean connected) {
                        Log.d("MainActivity", "BLE connection state: " + address + " = " + connected);
                    }
                });
        bleAdvertiser.startAdvertising();
        bleAdvertiser.startScanning();

        meshNetworkManager.start();
        updateNetworkStatus();
        startScanAnimation();

        // Set PacketHandler for LiveLocationService
        com.example.disastercomm.services.LiveLocationService.setPacketHandler(packetHandler);

        Log.d("MainActivity", "Mesh network initialized with BLE and connection pooling");
    }

    private void requestPermissions() {
        PermissionsManager.requestPermissions(this, REQUEST_CODE_REQUIRED_PERMISSIONS);

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!PermissionsManager.hasPermissions(this)) {
                Toast.makeText(this, "If permission dialog didn't appear, please enable permissions in Settings.",
                        Toast.LENGTH_LONG).show();
                try {
                    android.content.Intent intent = new android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    android.net.Uri uri = android.net.Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 3000);
    }

    private void sendSos() {
        if (packetHandler == null) {
            Toast.makeText(this, "Mesh not ready. Grant permissions first.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "Fetching location...", Toast.LENGTH_SHORT).show();
        locationHelper.getCurrentLocation((lat, lng) -> {
            String content = "SOS! HELP! Loc: " + lat + ", " + lng;
            Message sosMessage = new Message(DeviceUtil.getDeviceId(this), username, Message.Type.SOS, content);
            packetHandler.sendMessage(sosMessage);
            Toast.makeText(this, "SOS Sent with Location: " + lat + "," + lng, Toast.LENGTH_LONG).show();

            getSupportActionBar().setSubtitle(String.format("GPS: %.4f, %.4f", lat, lng));
        });
    }

    private com.google.android.material.bottomsheet.BottomSheetDialog chatDialog;
    private ChatAdapter chatAdapter;
    private RecyclerView rvChatMessages;

    private void showChatDialog() {
        if (packetHandler == null) {
            Toast.makeText(this, "Mesh not ready. Grant permissions first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (chatDialog == null) {
            chatDialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
            android.view.View view = getLayoutInflater().inflate(R.layout.dialog_chat, null);
            chatDialog.setContentView(view);

            rvChatMessages = view.findViewById(R.id.rvChatMessages);
            EditText etChatMessage = view.findViewById(R.id.etChatMessage);
            Button btnSendChat = view.findViewById(R.id.btnSendChat);

            chatAdapter = new ChatAdapter(DeviceUtil.getDeviceId(this), this);
            rvChatMessages.setLayoutManager(new LinearLayoutManager(this));
            rvChatMessages.setAdapter(chatAdapter);

            btnSendChat.setOnClickListener(v -> {
                String text = etChatMessage.getText().toString().trim();
                if (!text.isEmpty()) {
                    Message msg = new Message(DeviceUtil.getDeviceId(this), username, Message.Type.TEXT, text);
                    msg.status = Message.Status.SENT;
                    packetHandler.sendMessage(msg);
                    chatAdapter.addMessage(msg);
                    rvChatMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                    etChatMessage.setText("");
                }
            });
        }

        chatDialog.show();
    }

    private void startScanAnimation() {
        if (viewScanRipple == null)
            return;
        if (pulseAnimatorSet != null && pulseAnimatorSet.isRunning())
            return;

        viewScanRipple.setVisibility(android.view.View.VISIBLE);

        scaleXAnim = android.animation.ObjectAnimator.ofFloat(viewScanRipple, "scaleX", 1f, 3f);
        scaleYAnim = android.animation.ObjectAnimator.ofFloat(viewScanRipple, "scaleY", 1f, 3f);
        alphaAnim = android.animation.ObjectAnimator.ofFloat(viewScanRipple, "alpha", 0.5f, 0f);

        scaleXAnim.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        scaleYAnim.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        alphaAnim.setRepeatCount(android.animation.ObjectAnimator.INFINITE);

        scaleXAnim.setDuration(1500);
        scaleYAnim.setDuration(1500);
        alphaAnim.setDuration(1500);

        pulseAnimatorSet = new android.animation.AnimatorSet();
        pulseAnimatorSet.playTogether(scaleXAnim, scaleYAnim, alphaAnim);
        pulseAnimatorSet.start();
    }

    private void stopScanAnimation() {
        if (pulseAnimatorSet != null) {
            pulseAnimatorSet.cancel();
            if (viewScanRipple != null)
                viewScanRipple.setVisibility(android.view.View.INVISIBLE);
        }
    }

    @Override
    public void onDeviceConnected(String endpointId, String deviceName) {
        runOnUiThread(() -> {
            meshDeviceMap.put(endpointId, deviceName);
            updateNetworkStatus();
            refreshDeviceList();
            stopScanAnimation();

            // Share my location with new peer
            if (locationHelper != null) {
                locationHelper.getCurrentLocation((lat, lng) -> {
                    String locPayload = lat + "," + lng;
                    Message locMsg = new Message(DeviceUtil.getDeviceId(this), username, Message.Type.LOCATION_UPDATE,
                            locPayload);
                    locMsg.receiverId = endpointId;
                    packetHandler.sendMessage(locMsg);
                });
            }
        });
    }

    @Override
    public void onDeviceDisconnected(String endpointId) {
        runOnUiThread(() -> {
            meshDeviceMap.remove(endpointId);
            refreshDeviceList();
            PeerLocationManager.getInstance().removePeer(endpointId);
            updateNetworkStatus();
            if (meshNetworkManager.getConnectedEndpoints().isEmpty() && bluetoothDevices.isEmpty()) {
                startScanAnimation();
            }
        });
    }

    @Override
    public void onPayloadReceived(String endpointId, byte[] data) {
        packetHandler.handlePayload(endpointId, data);
    }

    @Override
    public void onMessageReceived(Message message) {
        runOnUiThread(() -> {
            // Handle delivery receipts
            if (message.type == Message.Type.DELIVERY_RECEIPT) {
                if (chatAdapter != null && message.receiptFor != null) {
                    chatAdapter.updateMessageStatus(message.receiptFor, Message.Status.DELIVERED);
                    notificationSoundManager.playDeliverySound();
                }
                return;
            }

            // Handle read receipts
            if (message.type == Message.Type.READ_RECEIPT) {
                if (chatAdapter != null && message.receiptFor != null) {
                    chatAdapter.updateMessageStatus(message.receiptFor, Message.Status.READ);
                }
                return;
            }

            if (message.type == Message.Type.LOCATION_UPDATE) {
                try {
                    String[] parts = message.content.split(",");
                    if (parts.length == 2) {
                        double lat = Double.parseDouble(parts[0]);
                        double lng = Double.parseDouble(parts[1]);

                        // Update peer location with live sharing metadata
                        PeerLocationManager.getInstance().updatePeerLocation(
                                message.senderId,
                                lat,
                                lng,
                                message.isLiveSharing,
                                message.sharingUntil);

                        // NOTIFY USER if this is a new live session
                        if (message.isLiveSharing) {
                            if (!notifiedLiveSharers.contains(message.senderId)) {
                                notifiedLiveSharers.add(message.senderId);
                                showLiveTrackingNotification(message.senderId, message.senderName);
                            }
                        } else {
                            // Reset if they stop sharing (optional, but handled by service usually)
                            notifiedLiveSharers.remove(message.senderId);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }

            // Send delivery receipt for text messages
            if (message.type == Message.Type.TEXT && !message.senderId.equals(DeviceUtil.getDeviceId(this))) {
                Message deliveryReceipt = Message.createDeliveryReceipt(
                        message.id,
                        DeviceUtil.getDeviceId(this),
                        username);
                packetHandler.sendMessage(deliveryReceipt);
            }

            // Priority handling for SOS
            if (message.type == Message.Type.SOS) {
                notificationSoundManager.playSosSound();
                // Use senderName if available, else ID
                String name = (message.senderName != null && !message.senderName.isEmpty()) ? message.senderName
                        : message.senderId.substring(0, Math.min(8, message.senderId.length()));
                String displayText = "SOS from " + name + ": " + message.content;
                new AlertDialog.Builder(this)
                        .setTitle("🚨 SOS RECEIVED")
                        .setMessage(displayText)
                        .setPositiveButton("OK", null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();

                if (chatAdapter != null) {
                    chatAdapter.addMessage(message);
                    if (rvChatMessages != null)
                        rvChatMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
                }
            } else if (message.type == Message.Type.TEXT) {
                // Regular Chat Message
                notificationSoundManager.playMessageSound();

                if (chatAdapter != null) {
                    chatAdapter.addMessage(message);
                    if (rvChatMessages != null)
                        rvChatMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

                    // Send read receipt if chat is open
                    if (chatDialog != null && chatDialog.isShowing()) {
                        Message readReceipt = Message.createReadReceipt(
                                message.id,
                                DeviceUtil.getDeviceId(this),
                                username);
                        packetHandler.sendMessage(readReceipt);
                    } else {
                        String name = (message.senderName != null && !message.senderName.isEmpty()) ? message.senderName
                                : message.senderId.substring(0, Math.min(8, message.senderId.length()));
                        Toast.makeText(this, "💬 New message from " + name, Toast.LENGTH_SHORT)
                                .show();
                    }
                } else {
                    String name = (message.senderName != null && !message.senderName.isEmpty()) ? message.senderName
                            : message.senderId.substring(0, Math.min(8, message.senderId.length()));
                    Toast.makeText(this, "💬 New message from " + name, Toast.LENGTH_SHORT)
                            .show();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationSoundManager != null)
            notificationSoundManager.release();
        if (networkStateMonitor != null)
            networkStateMonitor.stopMonitoring();
        if (bluetoothScanner != null)
            bluetoothScanner.stopScan();
        if (bleAdvertiser != null)
            bleAdvertiser.stop();
        if (bluetoothConnectionManager != null)
            bluetoothConnectionManager.stop();
        if (meshNetworkManager != null) {
            meshNetworkManager.stop();
        }
        if (packetHandler != null) {
            packetHandler.close();
        }
        // Cleanup connection pool
        if (connectionPoolManager != null) {
            connectionPoolManager.cleanupStaleConnections();
        }
    }

    private void showLiveTrackingNotification(String userId, String userName) {
        String name = (userName != null && !userName.isEmpty()) ? userName
                : userId.substring(0, Math.min(8, userId.length()));
        String content = "🔴 " + name + " is sharing live location. Tap to track.";

        android.content.Intent intent = new android.content.Intent(this, MapActivity.class);
        intent.putExtra("TARGET_USER_ID", userId);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, userId.hashCode(), intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);

        String channelId = "live_location_channel";
        android.app.NotificationManager nm = (android.app.NotificationManager) getSystemService(
                android.content.Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, "Live Updates",
                    android.app.NotificationManager.IMPORTANCE_HIGH);
            nm.createNotificationChannel(channel);
        }

        android.app.Notification notification = new androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setContentTitle("Live Location Started")
                .setContentText(content)
                .setSmallIcon(R.drawable.ic_members)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .build();

        nm.notify(userId.hashCode(), notification);
    }

    @Override
    public void onLocationClick(String userId) {
        android.content.Intent intent = new android.content.Intent(this, MapActivity.class);
        intent.putExtra("TARGET_USER_ID", userId);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_REQUIRED_PERMISSIONS) {
            if (PermissionsManager.hasPermissions(this)) {
                initMeshNetwork();
            }
        }
    }
}
