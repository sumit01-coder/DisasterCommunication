package com.example.disastercomm.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.disastercomm.R;
import com.example.disastercomm.data.AppDatabase;
import com.example.disastercomm.network.BluetoothConnectionManager;
import com.example.disastercomm.network.MeshNetworkManager;
import com.example.disastercomm.network.NetworkStateMonitor;
import com.example.disastercomm.network.PacketHandler;
import com.example.disastercomm.network.BLEAdvertiser;
import com.example.disastercomm.network.BLEHubClient; // New Hub Client
import com.example.disastercomm.utils.DeviceUtil;
import com.example.disastercomm.utils.NotificationHelper;
import com.example.disastercomm.utils.NotificationSoundManager;

public class NetworkService extends Service {
    private static final String TAG = "NetworkService";
    private static final String CHANNEL_ID = "NetworkServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    // Binder given to clients
    private final IBinder binder = new LocalBinder();

    // Network Managers
    private MeshNetworkManager meshNetworkManager;
    private BluetoothConnectionManager bluetoothConnectionManager;
    private BLEAdvertiser bleAdvertiser;
    private BLEHubClient bleHubClient; // Hub Client
    private PacketHandler packetHandler;
    private NetworkStateMonitor networkStateMonitor;
    private NotificationSoundManager notificationSoundManager;
    private NotificationHelper notificationHelper;

    private String username;

    public class LocalBinder extends Binder {
        public NetworkService getService() {
            return NetworkService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NetworkService Created");
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String user = intent.getStringExtra("username");
            if (user != null && !user.isEmpty()) {
                this.username = user;
                // Initialize if not already done
                if (meshNetworkManager == null) {
                    initManagers();
                }
            }
        }

        // Start as Foreground Service
        startForeground(NOTIFICATION_ID, getNotification());

        return START_NOT_STICKY;
    }

    private void initManagers() {
        Log.d(TAG, "Initializing Network Managers for user: " + username);

        // Database
        AppDatabase db = AppDatabase.getDatabase(this);

        // 1. Mesh Network
        meshNetworkManager = new MeshNetworkManager(this, username, new MeshNetworkManager.MeshCallback() {
            @Override
            public void onDeviceConnected(String endpointId, String deviceName) {
                // Forward to Activity via Broadcast or Callback
                broadcastUpdate("MESH_CONNECTED", endpointId, deviceName);
            }

            @Override
            public void onDeviceDisconnected(String endpointId) {
                broadcastUpdate("MESH_DISCONNECTED", endpointId, null);
            }

            @Override
            public void onPayloadReceived(String endpointId, byte[] payload) {
                if (packetHandler != null) {
                    packetHandler.handlePayload(endpointId, payload);
                }
            }
        });

        // 2. Packet Handler
        packetHandler = new PacketHandler(this, meshNetworkManager, db);
        // packetHandler.setMessageListener(...) -> Set by Activity later?
        // Or handle simple notifications here?

        // 3. Bluetooth
        bluetoothConnectionManager = new BluetoothConnectionManager(this,
                new BluetoothConnectionManager.BluetoothCallback() {
                    @Override
                    public void onBluetoothConnected(String address, String deviceName) {
                        broadcastUpdate("BT_CONNECTED", address, deviceName);
                    }

                    @Override
                    public void onBluetoothDisconnected(String address) {
                        broadcastUpdate("BT_DISCONNECTED", address, null);
                    }

                    @Override
                    public void onBluetoothDataReceived(String address, byte[] data) {
                        if (packetHandler != null) {
                            packetHandler.handlePayload(address, data);
                        }
                    }
                });
        packetHandler.setBluetoothManager(bluetoothConnectionManager);

        // 3.5 BLE Hub Client (For ESP32-S3)
        bleHubClient = new BLEHubClient(this, new BLEHubClient.HubCallback() {

            @Override
            public void onHubConnected(String address, String deviceName) {
                broadcastUpdate("BT_CONNECTED", address, deviceName); // Treat as BT connection
            }

            @Override
            public void onHubDisconnected() {
                // Determine ID? We just broadcast unknown or generic
                broadcastUpdate("BT_DISCONNECTED", "Hub", null);
            }

            @Override
            public void onHubMessageReceived(String message) {
                if (packetHandler != null) {
                    // Hub sends raw processing logic, maybe JSON lines?
                    // The ESP32 code sends "WiFi" or "BLE" prefixed strings or raw JSON?
                    // Looking at ESP32: pTxCharacteristic->setValue((uint8_t*)toSend.c_str()...
                    // It sends whatever it receives.
                    // If it's a JSON string, handlePayload expects byte[]
                    packetHandler.handlePayload("Hub", message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        });
        packetHandler.setBleHubClient(bleHubClient);

        // 4. BLE Advertiser (Fast Discovery)
        bleAdvertiser = new BLEAdvertiser(this, username, DeviceUtil.getDeviceId(this),
                new BLEAdvertiser.BLECallback() {
                    @Override
                    public void onBLEDeviceFound(String address, String name, int rssi) {
                        Log.d(TAG, "BLE Device Found: " + name + " (" + address + ")");
                        // Trigger fast pairing via Classic Bluetooth
                        if (name != null && name.contains("DisasterComm_S3")) {
                            Log.d(TAG, "⚡ Found ESP32 Hub! Connecting via GATT...");
                            android.bluetooth.BluetoothDevice device = android.bluetooth.BluetoothAdapter
                                    .getDefaultAdapter().getRemoteDevice(address);
                            bleHubClient.connect(device);
                            return;
                        }

                        // Trigger fast pairing via Classic Bluetooth (Standard Android Phones)
                        if (bluetoothConnectionManager != null) {
                            android.bluetooth.BluetoothDevice device = android.bluetooth.BluetoothAdapter
                                    .getDefaultAdapter().getRemoteDevice(address);
                            bluetoothConnectionManager.connectToDevice(device);
                        }
                    }

                    @Override
                    public void onBLEConnectionStateChanged(String address, boolean connected) {
                        // Log state change
                    }
                });

        // 5. Start Managers
        meshNetworkManager.start();
        bluetoothConnectionManager.start();
        bleAdvertiser.startAdvertising();
        bleAdvertiser.startScanning();

        Log.d(TAG, "Network Managers Started");
    }

    // Broadcast helper
    private void broadcastUpdate(String action, String id, String extra) {
        Intent intent = new Intent("com.example.disastercomm.NETWORK_UPDATE");
        intent.putExtra("action", action);
        intent.putExtra("id", id);
        if (extra != null)
            intent.putExtra("extra", extra);
        sendBroadcast(intent);
    }

    private Notification getNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DisasterComm Network")
                .setContentText("Maintaining mesh connections...")
                .setSmallIcon(R.drawable.ic_signal)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Network Service Channel",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (meshNetworkManager != null)
            meshNetworkManager.stop();
        if (bluetoothConnectionManager != null)
            bluetoothConnectionManager.stop();
        if (bleAdvertiser != null)
            bleAdvertiser.stop();
        if (bleHubClient != null)
            bleHubClient.disconnect();
        Log.d(TAG, "NetworkService Destroyed");
    }

    // Getters for binding
    public MeshNetworkManager getMeshManager() {
        return meshNetworkManager;
    }

    public BluetoothConnectionManager getBluetoothManager() {
        return bluetoothConnectionManager;
    }

    public PacketHandler getPacketHandler() {
        return packetHandler;
    }
}
