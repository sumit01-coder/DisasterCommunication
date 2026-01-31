package com.example.disastercomm.services;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Emergency Beacon Service - Ultra-low-power SOS broadcasting via BLE.
 * Can run for 24+ hours on minimal battery (< 5%) to aid rescue operations.
 */
public class EmergencyBeaconService extends Service {
    private static final String TAG = "EmergencyBeacon";

    // Custom UUID for Disaster Communication Mesh Network
    private static final UUID SERVICE_UUID = UUID.fromString("12345678-1234-5678-1234-56789abcdef0");
    private static final int MANUFACTURER_ID = 0xFFFF; // Custom manufacturer ID

    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;
    private boolean isBeaconing = false;

    private double lastLatitude = 0;
    private double lastLongitude = 0;
    private String deviceId = "UNKNOWN";

    @Override
    public void onCreate() {
        super.onCreate();

        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            }
        }

        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                isBeaconing = true;
                Log.d(TAG, "🚨 Emergency beacon STARTED");
            }

            @Override
            public void onStartFailure(int errorCode) {
                isBeaconing = false;
                Log.e(TAG, "❌ Beacon failed to start: " + errorCode);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            lastLatitude = intent.getDoubleExtra("latitude", 0);
            lastLongitude = intent.getDoubleExtra("longitude", 0);
            deviceId = intent.getStringExtra("deviceId");

            if (deviceId == null) {
                deviceId = "UNKNOWN";
            }

            startBeacon();
        }

        return START_STICKY; // Keep running even if app is killed
    }

    /**
     * Start emergency beacon broadcasting
     */
    private void startBeacon() {
        if (advertiser == null) {
            Log.e(TAG, "❌ BLE Advertiser not available");
            return;
        }

        if (isBeaconing) {
            Log.d(TAG, "⚠️ Beacon already running");
            return;
        }

        // Create manufacturer data payload
        byte[] manufacturerData = buildBeaconPayload();

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER) // Ultra-low power
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) // High range
                .setConnectable(false) // Don't waste power on connections
                .setTimeout(0) // Continuous
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false) // Save bytes
                .addServiceUuid(new ParcelUuid(SERVICE_UUID))
                .addManufacturerData(MANUFACTURER_ID, manufacturerData)
                .build();

        advertiser.startAdvertising(settings, data, advertiseCallback);
        Log.d(TAG, String.format("📡 Broadcasting SOS beacon: %.6f, %.6f", lastLatitude, lastLongitude));
    }

    /**
     * Build beacon payload with location and device info
     * Format: [2bytes: "DC"] [1byte: flags] [4bytes: lat] [4bytes: lng] [4bytes:
     * deviceIdHash]
     */
    private byte[] buildBeaconPayload() {
        ByteBuffer buffer = ByteBuffer.allocate(15);

        // Signature: "DC" (Disaster Comm)
        buffer.put((byte) 'D');
        buffer.put((byte) 'C');

        // Flags: bit 0 = SOS, bit 1 = LOW_BATTERY
        byte flags = 0x01; // SOS flag
        buffer.put(flags);

        // Latitude (float, 4 bytes)
        buffer.putFloat((float) lastLatitude);

        // Longitude (float, 4 bytes)
        buffer.putFloat((float) lastLongitude);

        // Device ID hash (4 bytes) - simple hash for identification
        int deviceHash = deviceId.hashCode();
        buffer.putInt(deviceHash);

        return buffer.array();
    }

    /**
     * Update beacon location
     */
    public void updateLocation(double lat, double lng) {
        if (lat != lastLatitude || lng != lastLongitude) {
            lastLatitude = lat;
            lastLongitude = lng;

            if (isBeaconing) {
                // Restart beacon with new location
                stopBeacon();
                startBeacon();
            }
        }
    }

    /**
     * Stop beacon
     */
    private void stopBeacon() {
        if (advertiser != null && isBeaconing) {
            advertiser.stopAdvertising(advertiseCallback);
            isBeaconing = false;
            Log.d(TAG, "🛑 Emergency beacon STOPPED");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopBeacon();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Not a bound service
    }

    /**
     * Parse beacon data (for scanner side)
     */
    public static BeaconData parseBeaconPayload(byte[] data) {
        if (data == null || data.length < 15) {
            return null;
        }

        // Check signature
        if (data[0] != 'D' || data[1] != 'C') {
            return null; // Not our beacon
        }

        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.get(); // Skip 'D'
        buffer.get(); // Skip 'C'

        BeaconData beacon = new BeaconData();
        beacon.flags = buffer.get();
        beacon.isSOS = (beacon.flags & 0x01) != 0;
        beacon.isLowBattery = (beacon.flags & 0x02) != 0;
        beacon.latitude = buffer.getFloat();
        beacon.longitude = buffer.getFloat();
        beacon.deviceHash = buffer.getInt();
        beacon.timestamp = System.currentTimeMillis();

        return beacon;
    }

    public static class BeaconData {
        public byte flags;
        public boolean isSOS;
        public boolean isLowBattery;
        public double latitude;
        public double longitude;
        public int deviceHash;
        public long timestamp;

        @Override
        public String toString() {
            return String.format("SOS Beacon: %.6f, %.6f %s%s",
                    latitude, longitude,
                    isSOS ? "[SOS]" : "",
                    isLowBattery ? "[LOW_BAT]" : "");
        }
    }
}
