package com.example.disastercomm;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername;
    private Spinner spinnerRole;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        com.example.disastercomm.utils.PreferenceManager pm = new com.example.disastercomm.utils.PreferenceManager(
                this);
        if (pm.isAppLockEnabled()) {
            // Show blank screen or loading
            setContentView(R.layout.activity_login);
            // In a real app we might want a dedicated "Locked" layout, but this works
            // Hide controls initially to prevent access?
            // Better: just auth. The prompt covers the screen.

            com.example.disastercomm.utils.BiometricHelper.authenticate(this,
                    new com.example.disastercomm.utils.BiometricHelper.Callback() {
                        @Override
                        public void onSuccess() {
                            proceedToApp();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(LoginActivity.this, "Authentication failed. Exiting.", Toast.LENGTH_SHORT)
                                    .show();
                            finish();
                        }
                    });
        } else {
            proceedToApp();
        }
    }

    private void proceedToApp() {
        // Check if already logged in
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        if (prefs.contains("username")) {
            startMainActivity();
            return;
        }

        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.etUsername);
        spinnerRole = findViewById(R.id.spinnerRole);
        btnLogin = findViewById(R.id.btnLogin);

        // Setup Role Spinner
        String[] roles = new String[] { "Civilian", "Rescue Team", "Medic", "Official" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, roles);
        spinnerRole.setAdapter(adapter);

        btnLogin.setOnClickListener(v -> {
            String name = etUsername.getText().toString().trim();
            if (name.isEmpty()) {
                etUsername.setError("Name is required");
                return;
            }

            String role = spinnerRole.getSelectedItem().toString();

            // Save Profile
            prefs.edit()
                    .putString("username", name)
                    .putString("role", role)
                    .apply();

            Toast.makeText(this, "Welcome, " + name, Toast.LENGTH_SHORT).show();
            startMainActivity();
        });
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivityNew.class);
        // Pass username to new activity
        SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        intent.putExtra("username", prefs.getString("username", "User"));
        startActivity(intent);
        finish();
    }
}
