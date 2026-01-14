package com.example.disastercomm.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import java.util.UUID;

import static android.content.Context.BATTERY_SERVICE;

public class DeviceUtil {

    private static final String PREF_NAME = "DisasterCommPrefs";
    private static final String KEY_DEVICE_ID = "device_id";

    public static String getDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String deviceId = prefs.getString(KEY_DEVICE_ID, null);
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
            // In a real app, we might append the model name (e.g., "Pixel 6") for better UX
            // but keep it random for now.
        }
        return deviceId;
    }
    
    public static int getBatteryRelease(Context context) {
         BatteryManager bm = (BatteryManager) context.getSystemService(BATTERY_SERVICE);
         return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }
}
