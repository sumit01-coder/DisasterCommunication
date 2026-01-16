package com.example.disastercomm.network;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.util.Log;
import androidx.core.app.ActivityCompat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BLE Advertiser for fast device discovery
 * Uses Bluetooth Low Energy for instant visibility (<1s discovery)
 */
public class BLEAdvertiser {
    private static final String TAG = "BLEAdvertiser";

    // Custom service UUID for disaster comm
    private static final UUID SERVICE_UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB");
    private static final UUID DEVICE_NAME_CHAR_UUID = UUID.fromString("00002A00-0000-1000-8000-00805F9B34FB");
    private static final UUID DEVICE_ID_CHAR_UUID = UUID.fromString("00002A01-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothLeScanner scanner;
    private BluetoothGattServer gattServer;
    private final String deviceName;
    private final String deviceId;
    private final BLECallback callback;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long RETRY_DELAY = 2000;
    private static final long SCAN_RESTART_INTERVAL = 30000; // Restart scan every 30s to stay fresh

    private boolean isAdvertising = false;
    private boolean isScanning = false;

    public interface BLECallback {
        void onBLEDeviceFound(String address, String name, int rssi);

        void onBLEConnectionStateChanged(String address, boolean connected);
    }

    public BLEAdvertiser(Context context, String deviceName, String deviceId, BLECallback callback) {
        this.context = context;
        this.deviceName = deviceName;
        this.deviceId = deviceId;
        this.callback = callback;
        this.bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        this.bluetoothAdapter = bluetoothManager.getAdapter();

        if (bluetoothAdapter != null) {
            this.advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            this.scanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    public void startAdvertising() {
        if (bluetoothAdapter == null || advertiser == null) {
            Log.e(TAG, "Bluetooth not available");
            Log.e(TAG, "BLE Advertising not supported on this device or Bluetooth not available");
            return;
        }

        if (isAdvertising) {
            Log.d(TAG, "Already advertising");
            return;
        }

        // Setup GATT Server
        setupGattServer();

        // OPTIMIZED: Connectable advertising for instant pairing
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // ~100ms intervals
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true) // ✅ FAST PAIRING: Enable instant GATT connection
                .setTimeout(0) // Advertise indefinitely
                .build();

        // OPTIMIZED: Fix for DATA_TOO_LARGE (Error 1)
        // 1. Packet 1: Service UUID (Critical for discovery)
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Disable System Name (e.g. "Samsung Galaxy S23 Ultra...")
                .addServiceUuid(new ParcelUuid(SERVICE_UUID)) // 128-bit UUID (18 bytes)
                .build();

        // 2. Packet 2: App Username (Custom Name)
        // We use Manufacturer Data to store the custom name "Amit" because
        // setIncludeDeviceName uses the system adapter name.
        byte[] nameBytes = deviceName.getBytes(StandardCharsets.UTF_8);

        // Truncate to safe limit (e.g. 20 bytes) to fit in 31 byte payload
        // 31 - 2 (Header) - 2 (Mfg ID) = 27 bytes max. Safe = 20.
        if (nameBytes.length > 20) {
            byte[] truncated = new byte[20];
            System.arraycopy(nameBytes, 0, truncated, 0, 20);
            nameBytes = truncated;
        }

        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Disable System Name
                .addManufacturerData(0xFFFF, nameBytes) // 0xFFFF = Test/Reserved ID
                .build();

        advertiser.startAdvertising(settings, data, scanResponse, advertiseCallback);
        Log.d(TAG, "BLE advertising started with Custom Name: " + deviceName);
    }

    public void stopAdvertising() {
        if (advertiser != null && isAdvertising) {
            if (ActivityCompat.checkSelfPermission(context,
                    android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
                advertiser.stopAdvertising(advertiseCallback);
                isAdvertising = false;
                Log.d(TAG, "BLE advertising stopped");
            }
        }

        if (gattServer != null) {
            if (ActivityCompat.checkSelfPermission(context,
                    android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                gattServer.close();
                gattServer = null;
            }
        }
    }

    public void startScanning() {
        if (bluetoothAdapter == null || scanner == null) {
            Log.e(TAG, "Bluetooth scanner not available");
            return;
        }

        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission");
            return;
        }

        if (isScanning) {
            Log.d(TAG, "Already scanning");
            return;
        }

        // Configure scan settings for fast discovery
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) // Aggressive scanning
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .build();

        // Filter for our service UUID
        List<ScanFilter> filters = new ArrayList<>();
        filters.add(new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(SERVICE_UUID))
                .build());

        // ✅ ALSO SCAN FOR ESP32 HUB (Nordic UART Service)
        // UUID must match ESP32_Hub.ino and BLEHubClient.java
        // 6E400001-B5A3-F393-E0A9-E50E24DCCA9E
        filters.add(new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(java.util.UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")))
                .build());

        scanner.startScan(filters, scanSettings, scanCallback);
        isScanning = true;
        Log.d(TAG, "BLE scanning started");

        // Schedule periodic restart
        handler.removeCallbacks(scanRestartRunnable);
        handler.postDelayed(scanRestartRunnable, SCAN_RESTART_INTERVAL);
    }

    private final Runnable scanRestartRunnable = new Runnable() {
        @Override
        public void run() {
            if (isScanning) {
                Log.d(TAG, "♻️ Restarting BLE scan to maintain freshness");
                stopScanning();
                startScanning();
            }
        }
    };

    public void stopScanning() {
        if (scanner != null && isScanning) {
            if (ActivityCompat.checkSelfPermission(context,
                    android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                scanner.stopScan(scanCallback);
                isScanning = false;
                handler.removeCallbacks(scanRestartRunnable); // Stop the loop
                Log.d(TAG, "BLE scanning stopped");
            }
        }
    }

    private void setupGattServer() {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        gattServer = bluetoothManager.openGattServer(context, gattServerCallback);
        if (gattServer == null) {
            Log.e(TAG, "❌ Failed to open GATT Server! BLE features may be limited.");
            return;
        }

        // Create service
        BluetoothGattService service = new BluetoothGattService(
                SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        // Device name characteristic
        BluetoothGattCharacteristic nameChar = new BluetoothGattCharacteristic(
                DEVICE_NAME_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        nameChar.setValue(deviceName.getBytes(StandardCharsets.UTF_8));
        service.addCharacteristic(nameChar);

        // Device ID characteristic
        BluetoothGattCharacteristic idChar = new BluetoothGattCharacteristic(
                DEVICE_ID_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ,
                BluetoothGattCharacteristic.PERMISSION_READ);
        idChar.setValue(deviceId.getBytes(StandardCharsets.UTF_8));
        service.addCharacteristic(idChar);

        gattServer.addService(service);
        Log.d(TAG, "GATT server setup complete");
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            isAdvertising = true;
            Log.d(TAG, "BLE advertising started successfully");
        }

        @Override
        public void onStartFailure(int errorCode) {
            isAdvertising = false;
            Log.e(TAG, "BLE advertising failed: " + errorCode + ". Retrying...");
            handler.postDelayed(() -> startAdvertising(), RETRY_DELAY);
        }
    };

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (ActivityCompat.checkSelfPermission(context,
                    android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            BluetoothDevice device = result.getDevice();
            String name = device.getName(); // System Name
            String address = device.getAddress();
            int rssi = result.getRssi();

            // Check for Custom Name in Manufacturer Data (0xFFFF)
            // This is how we send names now to avoid DATA_TOO_LARGE errors
            if (result.getScanRecord() != null) {
                byte[] customNameBytes = result.getScanRecord().getManufacturerSpecificData(0xFFFF);
                if (customNameBytes != null) {
                    name = new String(customNameBytes, StandardCharsets.UTF_8).trim(); // Override with custom name
                }
            }

            if (name != null && callback != null) {
                // Log.d(TAG, "BLE device found: " + name + " (" + address + "), RSSI: " +
                // rssi);
                callback.onBLEDeviceFound(address, name, rssi);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (ActivityCompat.checkSelfPermission(context,
                    android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            for (ScanResult result : results) {
                BluetoothDevice device = result.getDevice();
                String name = device.getName();
                String address = device.getAddress();
                int rssi = result.getRssi();

                if (name != null && callback != null) {
                    callback.onBLEDeviceFound(address, name, rssi);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "BLE scan failed: " + errorCode + ". Retrying...");
            isScanning = false;
            handler.postDelayed(() -> startScanning(), RETRY_DELAY);
        }
    };

    private final BluetoothGattServerCallback gattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (ActivityCompat.checkSelfPermission(context,
                    android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            String address = device.getAddress();
            boolean connected = (newState == BluetoothGatt.STATE_CONNECTED);

            Log.d(TAG, "GATT connection state changed: " + address + ", connected: " + connected);

            if (callback != null) {
                callback.onBLEConnectionStateChanged(address, connected);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattCharacteristic characteristic) {
            if (ActivityCompat.checkSelfPermission(context,
                    android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            if (gattServer != null) {
                gattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset,
                        characteristic.getValue());
            }
        }
    };

    // Helper: Build device info payload for advertisement
    private byte[] buildDeviceInfoPayload() {
        // Embed device ID (first 8 chars) in advertisement data
        String shortId = deviceId.length() > 8 ? deviceId.substring(0, 8) : deviceId;
        return shortId.getBytes(StandardCharsets.UTF_8);
    }

    // Helper: Check Bluetooth permissions
    private boolean hasBluetoothPermissions() {
        return ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(context,
                        android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    public void stop() {
        stopAdvertising();
        stopScanning();
    }
}
