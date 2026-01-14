package com.example.disastercomm.network;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.disastercomm.utils.DeviceUtil;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MeshNetworkManager {

    private static final String TAG = "MeshNetworkManager";
    private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;
    private static final String SERVICE_ID = "com.example.disastercomm.SERVICE_ID";

    private final Context context;
    private final ConnectionsClient connectionsClient;
    private final String deviceId;
    private String username;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long DISCOVERY_RESTART_DELAY = 2000; // OPTIMIZED: Reduced from 5000ms
    private static final long CONNECTION_RETRY_DELAY = 1000; // OPTIMIZED: Reduced from 3000ms
    private boolean isDiscoveryActive = false;
    private boolean isAdvertisingActive = false; // ✅ Track advertising state
    private ConnectionPoolManager poolManager;

    // Map of endpointID -> DeviceName
    private final Map<String, String> connectedEndpoints = new HashMap<>();

    // Map to temporarily store names of endpoints during connection initiation
    private final Map<String, String> pendingEndpointNames = new HashMap<>();

    public interface MeshCallback {
        void onDeviceConnected(String endpointId, String deviceName);

        void onDeviceDisconnected(String endpointId);

        void onPayloadReceived(String endpointId, byte[] data);
    }

    private MeshCallback callback;

    public MeshNetworkManager(Context context, String username, MeshCallback callback) {
        this.context = context;
        this.username = username;
        this.callback = callback;
        this.connectionsClient = Nearby.getConnectionsClient(context);
        this.deviceId = DeviceUtil.getDeviceId(context);
    }

    public void setConnectionPoolManager(ConnectionPoolManager poolManager) {
        this.poolManager = poolManager;
    }

    /**
     * ✅ Check if any device is connected via Mesh
     */
    public boolean isConnected() {
        return !connectedEndpoints.isEmpty();
    }

    public void start() {
        startAdvertising();
        startDiscovery();
    }

    public void stop() {
        isDiscoveryActive = false;
        isAdvertisingActive = false; // ✅ Reset advertising state
        handler.removeCallbacksAndMessages(null);
        stopAdvertising();
        stopDiscovery();
        connectionsClient.stopAllEndpoints();
        connectedEndpoints.clear();
        pendingEndpointNames.clear();
    }

    public void stopAdvertising() {
        if (isAdvertisingActive) {
            connectionsClient.stopAdvertising();
            isAdvertisingActive = false;
            Log.d(TAG, "Advertising stopped");
        }
    }

    public void stopDiscovery() {
        if (isDiscoveryActive) {
            connectionsClient.stopDiscovery();
            isDiscoveryActive = false;
            Log.d(TAG, "Discovery stopped");
        }
    }

    private void startAdvertising() {
        // ✅ Check state before starting
        if (isAdvertisingActive) {
            Log.d(TAG, "Advertising already active, skipping");
            return;
        }

        AdvertisingOptions advertisingOptions = new AdvertisingOptions.Builder().setStrategy(STRATEGY).build();

        connectionsClient
                .startAdvertising(
                        username + "__" + deviceId, // Name + UUID for unique identity
                        SERVICE_ID,
                        connectionLifecycleCallback,
                        advertisingOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            Log.d(TAG, "Advertising started successfully.");
                            isAdvertisingActive = true; // ✅ Update state
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            Log.e(TAG, "Advertising failed.", e);
                            isAdvertisingActive = false; // ✅ Reset state on failure
                        });
    }

    public void startDiscovery() {
        // ✅ Check state before starting
        if (isDiscoveryActive) {
            Log.d(TAG, "Discovery already active, skipping");
            return;
        }

        DiscoveryOptions discoveryOptions = new DiscoveryOptions.Builder().setStrategy(STRATEGY).build();

        connectionsClient
                .startDiscovery(
                        SERVICE_ID,
                        endpointDiscoveryCallback,
                        discoveryOptions)
                .addOnSuccessListener(
                        (Void unused) -> {
                            Log.d(TAG, "Discovery started successfully.");
                            isDiscoveryActive = true;
                        })
                .addOnFailureListener(
                        (Exception e) -> {
                            Log.e(TAG, "Discovery failed. Retrying in " + DISCOVERY_RESTART_DELAY + "ms", e);
                            isDiscoveryActive = false;
                            handler.postDelayed(this::startDiscovery, DISCOVERY_RESTART_DELAY);
                        });
    }

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            Log.d(TAG, "Endpoint found: " + endpointId + ", Name: " + info.getEndpointName());
            // We could store the name here too if we wanted to show a list before
            // connecting
            pendingEndpointNames.put(endpointId, info.getEndpointName());

            // Automatically request connection
            connectionsClient
                    .requestConnection(username, endpointId, connectionLifecycleCallback)
                    .addOnSuccessListener(
                            (Void unused) -> Log.d(TAG, "Connection requested."))
                    .addOnFailureListener(
                            (Exception e) -> Log.e(TAG, "Connection request failed.", e));
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            Log.d(TAG, "Endpoint lost: " + endpointId);
            pendingEndpointNames.remove(endpointId);
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo info) {
            Log.d(TAG, "Connection initiated: " + endpointId + ", Name: " + info.getEndpointName());
            // Store the name associated with this endpoint
            pendingEndpointNames.put(endpointId, info.getEndpointName());

            // Automatically accept connection
            connectionsClient.acceptConnection(endpointId, payloadCallback);
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution result) {
            switch (result.getStatus().getStatusCode()) {
                case ConnectionsStatusCodes.STATUS_OK:
                    Log.d(TAG, "Connected to: " + endpointId);
                    // Retrieve the name we stored earlier
                    String name = pendingEndpointNames.get(endpointId);
                    if (name == null) {
                        name = endpointId; // Fallback if name not found
                    }

                    connectedEndpoints.put(endpointId, name);

                    // Register with connection pool
                    if (poolManager != null) {
                        poolManager.addConnection(endpointId, name,
                                ConnectionPoolManager.TransportType.NEARBY_WIFI_DIRECT);
                    }

                    if (callback != null) {
                        final String finalName = name;
                        handler.post(() -> callback.onDeviceConnected(endpointId, finalName));
                    }
                    break;
                case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                    Log.d(TAG, "Connection rejected: " + endpointId + ". Retrying...");
                    pendingEndpointNames.remove(endpointId);
                    // Retry connection
                    handler.postDelayed(() -> requestConnection(endpointId), CONNECTION_RETRY_DELAY);
                    break;
                case ConnectionsStatusCodes.STATUS_ERROR:
                    Log.d(TAG, "Connection error: " + endpointId + ". Retrying...");
                    pendingEndpointNames.remove(endpointId);
                    // Retry connection
                    handler.postDelayed(() -> requestConnection(endpointId), CONNECTION_RETRY_DELAY);
                    break;
                default:
                    Log.d(TAG, "Unknown connection status: " + result.getStatus().getStatusCode());
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            Log.d(TAG, "Disconnected from: " + endpointId);
            connectedEndpoints.remove(endpointId);
            pendingEndpointNames.remove(endpointId);

            // Remove from pool
            if (poolManager != null) {
                poolManager.removeConnection(endpointId);
            }

            if (callback != null) {
                handler.post(() -> callback.onDeviceDisconnected(endpointId));
            }
        }
    };

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            if (payload.getType() == Payload.Type.BYTES) {
                byte[] bytes = payload.asBytes();
                Log.d(TAG, "Payload received from " + endpointId + ": " + new String(bytes, StandardCharsets.UTF_8));

                // Update pool manager
                if (poolManager != null) {
                    poolManager.recordMessageReceived(endpointId);
                    poolManager.updateLastSeen(endpointId);
                }

                if (callback != null) {
                    handler.post(() -> callback.onPayloadReceived(endpointId, bytes));
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            // Progress updates
        }
    };

    public void sendPayload(String endpointId, byte[] bytes) {
        long startTime = System.currentTimeMillis();
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
                .addOnSuccessListener((Void unused) -> {
                    if (poolManager != null) {
                        long latency = System.currentTimeMillis() - startTime;
                        poolManager.recordMessageSent(endpointId, latency);
                    }
                })
                .addOnFailureListener((Exception e) -> {
                    if (poolManager != null) {
                        poolManager.recordFailure(endpointId);
                    }
                });
    }

    public void broadcastPayload(byte[] bytes) {
        if (!connectedEndpoints.isEmpty()) {
            connectionsClient.sendPayload(new ArrayList<>(connectedEndpoints.keySet()), Payload.fromBytes(bytes));
        }
    }

    public List<String> getConnectedEndpoints() {
        return new ArrayList<>(connectedEndpoints.keySet());
    }

    public String getConnectedDeviceName(String endpointId) {
        return connectedEndpoints.get(endpointId);
    }

    private void requestConnection(String endpointId) {
        connectionsClient
                .requestConnection(username, endpointId, connectionLifecycleCallback)
                .addOnSuccessListener(
                        (Void unused) -> Log.d(TAG, "Connection requested (Retry) for " + endpointId))
                .addOnFailureListener(
                        (Exception e) -> Log.e(TAG, "Connection request failed for " + endpointId, e));
    }
}
