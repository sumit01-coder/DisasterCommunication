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
import com.google.android.material.textfield.TextInputEditText;
import android.widget.Button;

public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchCloudBackup;
    private SwitchMaterial switchAppLock;
    private TextInputEditText etHubName, etServiceUuid, etTxUuid, etRxUuid;
    private Button btnSaveHub, btnResetHub;
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
        etHubName = findViewById(R.id.etHubName);
        etServiceUuid = findViewById(R.id.etServiceUuid);
        etTxUuid = findViewById(R.id.etTxUuid);
        etRxUuid = findViewById(R.id.etRxUuid);
        btnSaveHub = findViewById(R.id.btnSaveHub);
        btnResetHub = findViewById(R.id.btnResetHub);

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

        // Hub Settings Listeners
        btnSaveHub.setOnClickListener(v -> saveHubSettings());
        btnResetHub.setOnClickListener(v -> resetHubSettings());

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

        // Hub Settings
        etHubName.setText(preferenceManager.getHubNameFilter());
        etServiceUuid.setText(preferenceManager.getHubServiceUuid());
        etTxUuid.setText(preferenceManager.getHubTxUuid());
        etRxUuid.setText(preferenceManager.getHubRxUuid());

        // Set Version Name
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            android.widget.TextView tvVersion = findViewById(R.id.tvVersion);
            tvVersion.setText("Version " + versionName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveHubSettings() {
        String name = etHubName.getText().toString().trim();
        String service = etServiceUuid.getText().toString().trim();
        String tx = etTxUuid.getText().toString().trim();
        String rx = etRxUuid.getText().toString().trim();

        if (name.isEmpty() || service.isEmpty() || tx.isEmpty() || rx.isEmpty()) {
            Toast.makeText(this, "Please fill all Hub fields", Toast.LENGTH_SHORT).show();
            return;
        }

        preferenceManager.setHubNameFilter(name);
        preferenceManager.setHubServiceUuid(service);
        preferenceManager.setHubTxUuid(tx);
        preferenceManager.setHubRxUuid(rx);

        Toast.makeText(this, "Hub Configuration Saved. Please restart the app to apply changes.", Toast.LENGTH_LONG)
                .show();
    }

    private void resetHubSettings() {
        etHubName.setText(PreferenceManager.DEFAULT_HUB_NAME_FILTER);
        etServiceUuid.setText(PreferenceManager.DEFAULT_HUB_SERVICE_UUID);
        etTxUuid.setText(PreferenceManager.DEFAULT_HUB_TX_UUID);
        etRxUuid.setText(PreferenceManager.DEFAULT_HUB_RX_UUID);

        saveHubSettings();
    }
}
