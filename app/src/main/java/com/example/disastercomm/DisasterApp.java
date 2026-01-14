package com.example.disastercomm;

import android.app.Application;

public class DisasterApp extends Application {
    private static DisasterApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Initialize CrashHandler
        com.example.disastercomm.utils.CrashHandler.init(this);

        androidx.appcompat.app.AppCompatDelegate
                .setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static DisasterApp getInstance() {
        return instance;
    }
}
