    // Manual device scan method
    private void triggerManualDeviceScan() {
        Log.d("DisasterApp", "Manual scan triggered");
        
        if (!hasPermissions()) {
            Toast.makeText(this, "Permissions required for scanning", Toast.LENGTH_SHORT).show();
            requestPermissions();
            return;
        }
        
        // 1. Restart mesh network discovery
        if (meshNetworkManager != null) {
            meshNetworkManager.stop();
            new android.os.Handler().postDelayed(() -> {
                if (meshNetworkManager != null) {
                    meshNetworkManager.start();
                    Log.d("DisasterApp", "Mesh network restarted for manual scan");
                }
            }, 500);
        }
        
        // 2. Restart Bluetooth discovery
        if (bluetoothConnectionManager != null) {
            bluetoothConnectionManager.stop();
            new android.os.Handler().postDelayed(() -> {
                if (bluetoothConnectionManager != null) {
                    bluetoothConnectionManager.start();
                    Log.d("DisasterApp", "Bluetooth restarted for manual scan");
                }
            }, 500);
        }
        
        // 3. Show feedback
        Toast.makeText(this, "Scanning all transports...", Toast.LENGTH_SHORT).show();
        
        // 4. Update UI after 3 seconds to show any new devices
        new android.os.Handler().postDelayed(() -> {
            int totalDevices = connectedMembers.size() + bluetoothDeviceMap.size();
            String message;
            if (totalDevices > 0) {
                message = "✓ Found " + totalDevices + " device(s)";
            } else {
                message = "No new devices found";
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }, 3000);
    }
