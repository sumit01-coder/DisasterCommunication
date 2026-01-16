package com.example.disastercomm.network;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import java.util.UUID;

/**
 * Handles communication with ESP32-S3 Hub via Nordic UART Service
 */
public class BLEHubClient {
    private static final String TAG = "BLEHubClient";

    // UUIDs matching the ESP32 Code
    public static final UUID SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    public static final UUID CHAR_TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E"); // Notify (Receive)
    public static final UUID CHAR_RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E"); // Write (Send)

    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context context;
    private final HubCallback callback;
    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic rxChar;
    private BluetoothGattCharacteristic txChar;
    private String connectedDeviceAddress;
    private boolean isConnected = false;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public interface HubCallback {
        void onHubConnected(String address, String deviceName);

        void onHubDisconnected();

        void onHubMessageReceived(String message);
    }

    public BLEHubClient(Context context, HubCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public void connect(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No permission to connect");
            return;
        }

        if (isConnected && device.getAddress().equals(connectedDeviceAddress)) {
            Log.d(TAG, "Already connected to this Hub");
            return;
        }

        disconnect(); // Clear existing

        Log.d(TAG, "Connecting to Hub: " + device.getName());
        bluetoothGatt = device.connectGatt(context, false, gattCallback);
    }

    public void disconnect() {
        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        isConnected = false;
        rxChar = null;
        txChar = null;
        connectedDeviceAddress = null;
    }

    public void sendData(String message) {
        if (!isConnected || rxChar == null || bluetoothGatt == null) {
            Log.w(TAG, "Cannot send, not connected to Hub");
            return;
        }

        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // ESP32 code expects '\n' as a delimiter
        String payload = message + "\n";
        rxChar.setValue(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rxChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); // Faster for ESP32
        bluetoothGatt.writeCharacteristic(rxChar);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (ActivityCompat.checkSelfPermission(context,
                    android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to GATT server. Discovering services...");
                gatt.discoverServices();
                connectedDeviceAddress = gatt.getDevice().getAddress();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server.");
                isConnected = false;
                if (callback != null) {
                    uiHandler.post(() -> callback.onHubDisconnected());
                }
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    rxChar = service.getCharacteristic(CHAR_RX_UUID);
                    txChar = service.getCharacteristic(CHAR_TX_UUID);

                    if (rxChar != null && txChar != null) {
                        Log.d(TAG, "Hub Services Found! Enabling notifications...");
                        isConnected = true;

                        // Enable Notifications
                        if (ActivityCompat.checkSelfPermission(context,
                                android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            gatt.setCharacteristicNotification(txChar, true);
                            BluetoothGattDescriptor descriptor = txChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                            if (descriptor != null) {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                gatt.writeDescriptor(descriptor);
                            }
                        }

                        // Notify Caller
                        if (callback != null) {
                            String name = gatt.getDevice().getName();
                            uiHandler.post(() -> callback.onHubConnected(gatt.getDevice().getAddress(), name));
                        }
                    } else {
                        Log.e(TAG, "Required characteristics not found!");
                    }
                } else {
                    Log.e(TAG, "Hub Service not found!");
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (CHAR_TX_UUID.equals(characteristic.getUuid())) {
                String message = new String(characteristic.getValue(), java.nio.charset.StandardCharsets.UTF_8);
                Log.d(TAG, "Msg from Hub: " + message);
                if (callback != null) {
                    uiHandler.post(() -> callback.onHubMessageReceived(message));
                }
            }
        }
    };
}
