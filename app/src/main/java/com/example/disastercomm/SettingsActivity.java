package com.example.disastercomm;

import android.os.Bundle;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.example.disastercomm.utils.BiometricHelper;
import com.example.disastercomm.utils.DeviceUtil;
import com.example.disastercomm.utils.PreferenceManager;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchCloudBackup;
    private SwitchMaterial switchAppLock;
    private PreferenceManager preferenceManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferenceManager = new PreferenceManager(this);

        initViews();
        loadSettings();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        switchCloudBackup = findViewById(R.id.switchCloudBackup);
        switchAppLock = findViewById(R.id.switchAppLock);

        // Listeners
        switchCloudBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferenceManager.setCloudBackupEnabled(isChecked);
            Toast.makeText(this, "Cloud Backup " + (isChecked ? "Enabled" : "Disabled"), Toast.LENGTH_SHORT).show();
        });

        switchAppLock.setOnClickListener(v -> {
            boolean isChecked = switchAppLock.isChecked();
            // Reset to previous state until authenticated
            switchAppLock.setChecked(!isChecked);

            if (!BiometricHelper.canAuthenticate(this)) {
                Toast.makeText(this, "Biometrics not available on this device", Toast.LENGTH_LONG).show();
                return;
            }

            BiometricHelper.authenticate(this, new BiometricHelper.Callback() {
                @Override
                public void onSuccess() {
                    preferenceManager.setAppLockEnabled(isChecked);
                    switchAppLock.setChecked(isChecked);
                    Toast.makeText(SettingsActivity.this, "App Lock " + (isChecked ? "Enabled" : "Disabled"),
                            Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(SettingsActivity.this, "Authentication failed: " + error, Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Software Update
        findViewById(R.id.layoutCheckUpdate).setOnClickListener(v -> {
            new com.example.disastercomm.utils.UpdateManager(this).checkForUpdates();
        });
    }

    private void loadSettings() {
        // Backup
        switchCloudBackup.setChecked(preferenceManager.isCloudBackupEnabled());

        // App Lock
        switchAppLock.setChecked(preferenceManager.isAppLockEnabled());
    }

}
