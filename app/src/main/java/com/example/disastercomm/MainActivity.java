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

    private final android.content.BroadcastReceiver batteryReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            if (android.content.Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                int level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
                float batteryPct = level * 100 / (float) scale;
                
                if (batteryPct <= 15.0f && meshNetworkManager != null) {
                    Log.w("MainActivity", "🔋 CRITICAL BATTERY: Enabling Extreme Battery Saver Mode.");
                    meshNetworkManager.setLowPowerMode(true);
                    Toast.makeText(context, "Battery critical! Entering Mesh Survival Mode.", Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load Username and Role
        android.content.SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        username = prefs.getString("username", "Unknown");
        String currentRole = prefs.getString("user_role", null);

        setContentView(R.layout.activity_main);

        if (currentRole == null) {
            showRoleSelectionDialog();
        }

        tvStatus = findViewById(R.id.tvStatus);
        viewScanRipple = findViewById(R.id.viewScanRipple);
        rvPeers = findViewById(R.id.rvPeers);
        btnSos = findViewById(R.id.btnSos);
        btnChat = findViewById(R.id.btnChat);
        Button btnMap = findViewById(R.id.btnMap);
        Button btnGlobalAlert = findViewById(R.id.btnGlobalAlert);

        if ("RESCUE".equals(currentRole)) {
            btnGlobalAlert.setVisibility(android.view.View.VISIBLE);
            btnGlobalAlert.setOnClickListener(v -> sendGlobalAlert());
        }

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

        com.google.android.material.switchmaterial.SwitchMaterial swLowPowerMode = findViewById(R.id.swLowPowerMode);
        swLowPowerMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (meshNetworkManager != null) {
                meshNetworkManager.setLowPowerMode(isChecked);
            }
            // Save state so MapActivity can pick it up
            getSharedPreferences("UserPrefs", MODE_PRIVATE).edit().putBoolean("low_power_mode", isChecked).apply();
            
            if (isChecked) {
                Toast.makeText(this, "Low Power Mode ENABLED. Scanning paused.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Low Power Mode DISABLED. Scanning resumed.", Toast.LENGTH_SHORT).show();
            }
        });

        // Update Title with Greeting
        getSupportActionBar().setTitle("D-Comm: " + username);

        // Listen for internal map intents to broadcast
        android.content.IntentFilter filter = new android.content.IntentFilter("com.example.disastercomm.SEND_MESH_MESSAGE");
        androidx.core.content.ContextCompat.registerReceiver(this, meshBroadcastReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);

        // Register Battery Monitor for Extreme Survival Mode
        android.content.IntentFilter batteryFilter = new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, batteryFilter);
    } // End of onCreate

    private final android.content.BroadcastReceiver meshBroadcastReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            String type = intent.getStringExtra("type");
            String content = intent.getStringExtra("content");
            if ("MAP_MARKER".equals(type) && packetHandler != null) {
                Message msg = new Message(DeviceUtil.getDeviceId(MainActivity.this), username, Message.Type.MAP_MARKER, content);
                packetHandler.sendMessage(msg);
            }
        }
    };

    private void showRoleSelectionDialog() {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_role_selection, null);
        dialog.setContentView(view);
        dialog.setCancelable(false); // Force selection

        android.view.View.OnClickListener roleClickListener = v -> {
            String role = "CIVILIAN";
            int id = v.getId();
            if (id == R.id.cardRoleRescue) role = "RESCUE";
            else if (id == R.id.cardRoleMedical) role = "MEDICAL";
            else if (id == R.id.cardRoleVolunteer) role = "VOLUNTEER";

            getSharedPreferences("UserPrefs", MODE_PRIVATE).edit().putString("user_role", role).apply();
            Toast.makeText(this, "Role saved: " + role, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        };

        view.findViewById(R.id.cardRoleCivilian).setOnClickListener(roleClickListener);
        view.findViewById(R.id.cardRoleRescue).setOnClickListener(roleClickListener);
        view.findViewById(R.id.cardRoleMedical).setOnClickListener(roleClickListener);
        view.findViewById(R.id.cardRoleVolunteer).setOnClickListener(roleClickListener);

        dialog.show();
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

    private void sendGlobalAlert() {
        if (packetHandler == null) {
            Toast.makeText(this, "Mesh network not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new AlertDialog.Builder(this)
            .setTitle("SEND GLOBAL BROADCAST?")
            .setMessage("This will trigger a maximum volume siren on ALL connected devices. Only use for extreme emergencies.")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("BROADCAST", (dialog, which) -> {
                Message alert = new Message(DeviceUtil.getDeviceId(this), username, Message.Type.GOVT_ALERT, "EVACUATE IMMEDIATELY");
                packetHandler.sendMessage(alert);
                Toast.makeText(this, "GLOBAL ALERT SENT", Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
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
            
            // Phase 5: "Store and Forward" Messaging
            // Sync critical hazard and SOS messages to newly connected peers so they catch up on history.
            java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                com.example.disastercomm.data.AppDatabase db = com.example.disastercomm.data.AppDatabase.getDatabase(this);
                if (db != null && packetHandler != null) {
                    java.util.List<Message> toForward = new java.util.ArrayList<>();
                    
                    // Fetch recent critical messages (limit to 10 each to avoid flooding)
                    toForward.addAll(db.messageDao().getRecentSosMessages(10));
                    toForward.addAll(db.messageDao().getRecentMapMarkers(10));
                    toForward.addAll(db.messageDao().getRecentGovtAlerts(10));
                    
                    for (Message msg : toForward) {
                        // Reset TTL to ensure it gets relayed slightly further but doesn't live forever
                        msg.ttl = 3; 
                        packetHandler.sendMessage(msg);
                    }
                    Log.d("StoreAndForward", "Forwarded " + toForward.size() + " historical critical messages to mesh.");
                }
            });
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

            if (message.type == Message.Type.GOVT_ALERT) {
                // Bypass silent mode and trigger maximum volume siren
                notificationSoundManager.playGovtAlert();
                
                runOnUiThread(() -> {
                    new AlertDialog.Builder(this)
                        .setTitle("🚨 GLOBAL EMERGENCY ALERT 🚨")
                        .setMessage("From Rescue Worker " + message.senderName + ":\n\n" + message.content)
                        .setPositiveButton("OK", null)
                        .setCancelable(false)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show();
                });
                return;
            }

            if (message.type == Message.Type.MAP_MARKER) {
                try {
                    String[] parts = message.content.split("\\|");
                    if (parts.length == 4) {
                        String emoji = parts[0];
                        String title = parts[1];
                        double lat = Double.parseDouble(parts[2]);
                        double lng = Double.parseDouble(parts[3]);
                        
                        PeerLocationManager.getInstance().addHazard(message.id, emoji, title, lat, lng);
                        Toast.makeText(this, "⚠️ New Hazard Reported: " + title, Toast.LENGTH_SHORT).show();
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
        try {
            unregisterReceiver(meshBroadcastReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
        }
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException e) {
            // Receiver not registered
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
