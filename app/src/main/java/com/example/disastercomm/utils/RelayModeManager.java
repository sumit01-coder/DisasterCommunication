package com.example.disastercomm.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.util.Log;

/**
 * Manages automatic relay mode activation for mesh network.
 * Detects stationary devices and optimizes them as network relays.
 */
public class RelayModeManager implements SensorEventListener {
    private static final String TAG = "RelayModeManager";
    private static final long STATIONARY_THRESHOLD_MS = 10 * 60 * 1000; // 10 minutes
    private static final float MOVEMENT_THRESHOLD = 0.5f; // m/s²
    private static final int MIN_BATTERY_PERCENT = 20;

    private final Context context;
    private final RelayModeCallback callback;
    private final SensorManager sensorManager;
    private final Sensor accelerometer;

    private boolean isRelayMode = false;
    private boolean manualOverride = false;
    private long lastMovementTime = System.currentTimeMillis();
    private float lastAcceleration = 0f;

    public interface RelayModeCallback {
        void onRelayModeChanged(boolean enabled);
    }

    public RelayModeManager(Context context, RelayModeCallback callback) {
        this.context = context;
        this.callback = callback;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /**
     * Start monitoring for relay mode conditions
     */
    public void startMonitoring() {
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG, "📡 Started relay mode monitoring");
        }
    }

    /**
     * Stop monitoring
     */
    public void stopMonitoring() {
        sensorManager.unregisterListener(this);
        Log.d(TAG, "⏸️ Stopped relay mode monitoring");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            // Calculate acceleration magnitude (excluding gravity)
            float acceleration = (float) Math.sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH;
            acceleration = Math.abs(acceleration);

            // Detect significant movement
            if (acceleration > MOVEMENT_THRESHOLD) {
                lastMovementTime = System.currentTimeMillis();
                lastAcceleration = acceleration;

                // If we were in auto relay mode, exit it
                if (isRelayMode && !manualOverride) {
                    setRelayMode(false, false);
                    Log.d(TAG, "🚶 Movement detected, exiting relay mode");
                }
            }

            // Check if device has been stationary long enough
            long stationaryTime = System.currentTimeMillis() - lastMovementTime;
            if (!isRelayMode && stationaryTime > STATIONARY_THRESHOLD_MS && shouldActivateRelay()) {
                setRelayMode(true, false);
                Log.d(TAG,
                        "🛑 Device stationary for " + (stationaryTime / 1000 / 60) + " minutes, activating relay mode");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not needed
    }

    /**
     * Check if conditions are right for relay mode
     */
    private boolean shouldActivateRelay() {
        // Check battery level
        int batteryPercent = getBatteryLevel();
        if (batteryPercent < MIN_BATTERY_PERCENT && !isCharging()) {
            Log.d(TAG, "⚠️ Battery too low for relay mode: " + batteryPercent + "%");
            return false;
        }

        return true;
    }

    /**
     * Manually enable/disable relay mode
     */
    public void setRelayMode(boolean enabled, boolean manual) {
        if (isRelayMode == enabled) {
            return; // No change
        }

        isRelayMode = enabled;
        manualOverride = manual;

        Log.d(TAG, (enabled ? "✅ Relay mode ENABLED" : "❌ Relay mode DISABLED") +
                (manual ? " (manual)" : " (auto)"));

        if (callback != null) {
            callback.onRelayModeChanged(enabled);
        }
    }

    /**
     * Get current relay mode status
     */
    public boolean isRelayMode() {
        return isRelayMode;
    }

    /**
     * Check if manually overridden
     */
    public boolean isManualOverride() {
        return manualOverride;
    }

    /**
     * Get battery level percentage
     */
    private int getBatteryLevel() {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        }
        return 100; // Assume full if unknown
    }

    /**
     * Check if device is charging
     */
    private boolean isCharging() {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm != null) {
            int status = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
            return status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;
        }
        return false;
    }

    /**
     * Get time since last movement (in seconds)
     */
    public long getStationaryDuration() {
        return (System.currentTimeMillis() - lastMovementTime) / 1000;
    }

    /**
     * Get relay mode statistics
     */
    public RelayStats getStats() {
        RelayStats stats = new RelayStats();
        stats.isRelayMode = isRelayMode;
        stats.isManual = manualOverride;
        stats.batteryLevel = getBatteryLevel();
        stats.isCharging = isCharging();
        stats.stationarySeconds = getStationaryDuration();
        return stats;
    }

    public static class RelayStats {
        public boolean isRelayMode;
        public boolean isManual;
        public int batteryLevel;
        public boolean isCharging;
        public long stationarySeconds;
    }
}
