package com.example.disastercomm.utils;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import java.util.HashSet;
import java.util.Set;

public class BluetoothScanner {

    public interface BluetoothScanListener {
        void onDeviceFound(String name, String address);
    }

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final BluetoothScanListener listener;
    private final Set<String> foundAddresses = new HashSet<>();
    private boolean isScanning = false;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    if (ActivityCompat.checkSelfPermission(context,
                            android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        // Permission should be checked by caller, but safety check here
                        // return;
                    }
                    String name = device.getName();
                    String address = device.getAddress();
                    if (name != null && !foundAddresses.contains(address)) {
                        foundAddresses.add(address);
                        listener.onDeviceFound(name, address);
                    }
                }
            }
        }
    };

    public BluetoothScanner(Context context, BluetoothScanListener listener) {
        this.context = context;
        this.listener = listener;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void startScan() {
        if (bluetoothAdapter == null || isScanning)
            return;

        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            return; // Caller must ensure permissions
        }

        foundAddresses.clear();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(receiver, filter);
        bluetoothAdapter.startDiscovery();
        isScanning = true;
    }

    public void stopScan() {
        if (!isScanning)
            return;

        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter.cancelDiscovery();
        }

        try {
            context.unregisterReceiver(receiver);
        } catch (Exception e) {
            // Already unregistered
        }
        isScanning = false;
    }
}
