package com.example.disastercomm.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String PREF_NAME = "DisasterCommPrefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_CLOUD_BACKUP = "cloud_backup";
    private static final String KEY_DEVICE_ID = "device_id";

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
}
