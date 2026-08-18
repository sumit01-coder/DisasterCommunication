package com.example.disastercomm.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREF_NAME = "DisasterCommPrefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_CLOUD_BACKUP = "cloud_backup";
    private static final String KEY_DEVICE_ID = "device_id";

    // BLE Hub UUIDs
    private static final String KEY_HUB_SERVICE_UUID = "hub_service_uuid";
    private static final String KEY_HUB_TX_UUID = "hub_tx_uuid";
    private static final String KEY_HUB_RX_UUID = "hub_rx_uuid";
    private static final String KEY_HUB_NAME_FILTER = "hub_name_filter";

    // Default UUIDs (Nordic UART Service)
    public static final String DEFAULT_HUB_SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
    public static final String DEFAULT_HUB_TX_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E";
    public static final String DEFAULT_HUB_RX_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E";
    public static final String DEFAULT_HUB_NAME_FILTER = "DisasterComm_S3";

    private final SharedPreferences prefs;

    public PreferenceManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void setUsername(String username) {
        prefs.edit().putString(KEY_USERNAME, username).apply();
    }

    public String getUsername(String defaultName) {
        return prefs.getString(KEY_USERNAME, defaultName);
    }

    public void setDarkMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply();
    }

    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, false); // Default to system/false
    }

    public void setCloudBackupEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CLOUD_BACKUP, enabled).apply();
    }

    public boolean isCloudBackupEnabled() {
        return prefs.getBoolean(KEY_CLOUD_BACKUP, false); // Default off for privacy
    }

    public void setAppLockEnabled(boolean enabled) {
        prefs.edit().putBoolean("app_lock", enabled).apply();
    }

    public boolean isAppLockEnabled() {
        return prefs.getBoolean("app_lock", false);
    }

    // BLE Hub Getters/Setters
    public String getHubServiceUuid() {
        return prefs.getString(KEY_HUB_SERVICE_UUID, DEFAULT_HUB_SERVICE_UUID);
    }

    public void setHubServiceUuid(String uuid) {
        prefs.edit().putString(KEY_HUB_SERVICE_UUID, uuid).apply();
    }

    public String getHubTxUuid() {
        return prefs.getString(KEY_HUB_TX_UUID, DEFAULT_HUB_TX_UUID);
    }

    public void setHubTxUuid(String uuid) {
        prefs.edit().putString(KEY_HUB_TX_UUID, uuid).apply();
    }

    public String getHubRxUuid() {
        return prefs.getString(KEY_HUB_RX_UUID, DEFAULT_HUB_RX_UUID);
    }

    public void setHubRxUuid(String uuid) {
        prefs.edit().putString(KEY_HUB_RX_UUID, uuid).apply();
    }

    public String getHubNameFilter() {
        return prefs.getString(KEY_HUB_NAME_FILTER, DEFAULT_HUB_NAME_FILTER);
    }

    public void setHubNameFilter(String name) {
        prefs.edit().putString(KEY_HUB_NAME_FILTER, name).apply();
    }
}
