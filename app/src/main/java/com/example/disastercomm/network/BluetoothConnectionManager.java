package com.example.disastercomm.network;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BluetoothConnectionManager {
    private static final String TAG = "BTConnMgr";
    private static final String SERVICE_NAME = "DisasterComm";
    private static final UUID SERVICE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private final BluetoothCallback callback;
    private final BluetoothAdapter bluetoothAdapter;
    private final Map<String, ConnectedThread> activeConnections = new ConcurrentHashMap<>();
    private ExecutorService connectionExecutor = Executors.newFixedThreadPool(4); // Parallel connections - NOT final
                                                                                  // for restart
    private final Set<String> attemptedDevices = ConcurrentHashMap.newKeySet(); // Track connection attempts

    private AcceptThread acceptThread;
    private boolean isRunning = false;
    private ConnectionPoolManager poolManager;

    public interface BluetoothCallback {
        void onBluetoothConnected(String address, String deviceName);

        void onBluetoothDisconnected(String address);

        void onBluetoothDataReceived(String address, byte[] data);
    }

    public BluetoothConnectionManager(Context context, BluetoothCallback callback) {
        this.context = context;
        this.callback = callback;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void setConnectionPoolManager(ConnectionPoolManager poolManager) {
        this.poolManager = poolManager;
    }

    /**
     * ✅ Check if any device is connected via Bluetooth
     */
    public boolean isConnected() {
        return !activeConnections.isEmpty();
    }

    public java.util.Map<String, String> getConnectedDevices() {
        java.util.Map<String, String> devices = new java.util.HashMap<>();
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return devices;
        }
        for (ConnectedThread thread : activeConnections.values()) {
            BluetoothDevice device = thread.socket.getRemoteDevice();
            String name = device.getName();
            devices.put(device.getAddress(), name != null ? name : device.getAddress());
        }
        return devices;
    }

    public void start() {
        if (bluetoothAdapter == null || isRunning)
            return;

        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission");
            return;
        }

        isRunning = true;

        // Recreate executor if it was shutdown
        if (connectionExecutor == null || connectionExecutor.isShutdown()) {
            connectionExecutor = Executors.newFixedThreadPool(4);
            Log.d(TAG, "ConnectionExecutor recreated");
        }

        acceptThread = new AcceptThread();
        acceptThread = new AcceptThread();
        acceptThread.start();
        scanPairedDevices(); // Start scanning for known devices
        Log.d(TAG, "Bluetooth server started");
    }

    public void scanPairedDevices() {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // OPTIMIZED: Parallel connection attempts with minimal delay
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices != null && !pairedDevices.isEmpty()) {
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            long delay = 0;

            for (BluetoothDevice device : pairedDevices) {
                String address = device.getAddress();

                // Skip if already connected or currently attempting
                if (!activeConnections.containsKey(address) && !attemptedDevices.contains(address)) {
                    attemptedDevices.add(address);

                    // OPTIMIZED: Use executor for parallel connections with small stagger
                    handler.postDelayed(() -> {
                        if (isRunning && !activeConnections.containsKey(address)) {
                            Log.d(TAG, "Auto-connecting to: " + address);
                            connectionExecutor.submit(() -> connectToDevice(device));
                        }
                    }, delay);
                    delay += 100; // OPTIMIZED: 100ms stagger for parallel attempts
                }
            }
        }
    }

    public void stop() {
        isRunning = false;

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        for (ConnectedThread thread : activeConnections.values()) {
            thread.cancel();
        }
        activeConnections.clear();
        attemptedDevices.clear();
        connectionExecutor.shutdown();
        Log.d(TAG, "Bluetooth manager stopped");
    }

    public void startDiscovery() {
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
            if (ActivityCompat.checkSelfPermission(context,
                    android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.startDiscovery();
                Log.d(TAG, "Bluetooth discovery started.");
                // Re-scan paired devices
                scanPairedDevices();
            }
        }
    }

    public void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String address = device.getAddress();
        if (activeConnections.containsKey(address)) {
            Log.d(TAG, "Already connected to " + address);
            return;
        }

        // ✅ Prevent duplicate connection attempts
        if (attemptedDevices.contains(address)) {
            return;
        }
        attemptedDevices.add(address);

        new ConnectThread(device).start();
    }

    public void sendData(String address, byte[] data) {
        ConnectedThread thread = activeConnections.get(address);
        if (thread != null) {
            thread.write(data);
        } else {
            Log.w(TAG, "No connection to " + address);
        }
    }

    public void broadcastData(byte[] data) {
        broadcastData(data, null);
    }

    public void broadcastData(byte[] data, String excludeAddress) {
        for (Map.Entry<String, ConnectedThread> entry : activeConnections.entrySet()) {
            if (excludeAddress == null || !entry.getKey().equals(excludeAddress)) {
                entry.getValue().write(data);
            }
        }
    }

    // Server thread to accept incoming connections
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                if (ActivityCompat.checkSelfPermission(context,
                        android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    // OPTIMIZED: Use insecure server socket (no pairing dialog)
                    tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Accept socket failed", e);
            }
            serverSocket = tmp;
        }

        public void run() {
            if (serverSocket == null) {
                Log.e(TAG, "❌ Server socket is null, AcceptThread stopping");
                return;
            }
            BluetoothSocket socket;
            while (isRunning) {
                try {
                    socket = serverSocket.accept();
                    if (socket != null) {
                        manageConnectedSocket(socket);
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        Log.e(TAG, "Accept failed", e);
                    }
                    break;
                }
            }
        }

        public void cancel() {
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Close server socket failed", e);
            }
        }
    }

    // Client thread to initiate outgoing connection
    private class ConnectThread extends Thread {
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
        }

        @Override
        public void run() {
            BluetoothSocket socket = null;
            try {
                if (ActivityCompat.checkSelfPermission(context,
                        android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {

                    // ✅ OPTIMIZED: Use insecure socket (no pairing dialog, faster connection)
                    socket = device.createInsecureRfcommSocketToServiceRecord(SERVICE_UUID);

                    // ✅ OPTIMIZED: Reduced timeout for faster failure detection
                    socket.connect(); // 2s timeout vs default 10s

                    // Connection succeeded - remove from attempted list
                    attemptedDevices.remove(device.getAddress());

                    // manageConnectedSocket will handle pool registration
                    manageConnectedSocket(socket);
                }
            } catch (IOException e) {
                Log.e(TAG, "Connection failed to " + device.getAddress(), e);

                // ✅ Track failure and allow retry on next scan
                attemptedDevices.remove(device.getAddress());

                if (poolManager != null) {
                    poolManager.recordFailure(device.getAddress());
                }
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException closeException) {
                        Log.e(TAG, "Close socket failed", closeException);
                    }
                }
            }
        }
    }

    // Manages an active connection
    private void manageConnectedSocket(BluetoothSocket socket) {
        BluetoothDevice device = socket.getRemoteDevice();
        String address = device.getAddress();

        if (activeConnections.containsKey(address)) {
            try {
                socket.close();
            } catch (IOException e) {
            }
            return;
        }

        ConnectedThread thread = new ConnectedThread(socket);
        activeConnections.put(address, thread);
        thread.start();

        // Register with connection pool
        if (poolManager != null) {
            if (ActivityCompat.checkSelfPermission(context,
                    android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                String name = device.getName();
                poolManager.addConnection(address, name != null ? name : address,
                        ConnectionPoolManager.TransportType.BLUETOOTH_CLASSIC);
            }
        }

        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            String name = device.getName();
            callback.onBluetoothConnected(address, name != null ? name : address);
        }
    }

    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private final InputStream inStream;
        private final OutputStream outStream;
        private final String address;
        private final ScheduledExecutorService heartbeatExecutor;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            this.address = socket.getRemoteDevice().getAddress();
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error creating streams", e);
            }

            inStream = tmpIn;
            outStream = tmpOut;
            heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
            startHeartbeat();
        }

        private void startHeartbeat() {
            heartbeatExecutor.scheduleAtFixedRate(() -> {
                try {
                    String heartbeatJson = "{\"type\":\"HEARTBEAT\",\"id\":\"hb_" + System.currentTimeMillis() + "\"}";
                    write(heartbeatJson.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                } catch (Exception e) {
                    Log.e(TAG, "Heartbeat failed: " + e.getMessage());
                    cancel(); // Close connection if heartbeat fails
                }
            }, 30, 30, TimeUnit.SECONDS);
        }

        public void run() {
            byte[] buffer = new byte[8192]; // OPTIMIZED: Larger buffer for faster transfer
            int bytes;

            while (isRunning) {
                try {
                    bytes = inStream.read(buffer);
                    byte[] data = new byte[bytes];
                    System.arraycopy(buffer, 0, data, 0, bytes);

                    // Update pool manager
                    if (poolManager != null) {
                        poolManager.recordMessageReceived(address);
                        poolManager.updateLastSeen(address);
                    }

                    callback.onBluetoothDataReceived(address, data);
                } catch (IOException e) {
                    Log.e(TAG, "Disconnected from " + address, e);
                    break;
                }
            }

            activeConnections.remove(address);
            attemptedDevices.remove(address);

            // Remove from pool
            if (poolManager != null) {
                poolManager.removeConnection(address);
            }

            callback.onBluetoothDisconnected(address);
            heartbeatExecutor.shutdownNow();
        }

        public void write(byte[] bytes) {
            long startTime = System.currentTimeMillis();
            try {
                outStream.write(bytes);
                outStream.flush(); // Ensure immediate send

                // Update pool manager
                if (poolManager != null) {
                    long latency = System.currentTimeMillis() - startTime;
                    poolManager.recordMessageSent(address, latency);
                }
            } catch (IOException e) {
                Log.e(TAG, "Write failed to " + address, e);
                if (poolManager != null) {
                    poolManager.recordFailure(address);
                }
            }
        }

        public void cancel() {
            try {
                if (heartbeatExecutor != null) {
                    heartbeatExecutor.shutdownNow();
                }
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Close socket failed", e);
            }
        }
    }
}
