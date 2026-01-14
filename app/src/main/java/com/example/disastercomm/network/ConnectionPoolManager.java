package com.example.disastercomm.network;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages connection pool across all transports (BT, BLE, Nearby)
 * Tracks connection quality and routes messages intelligently
 */
public class ConnectionPoolManager {
    private static final String TAG = "ConnPoolMgr";

    public enum TransportType {
        BLUETOOTH_CLASSIC,
        BLUETOOTH_LE,
        NEARBY_WIFI_DIRECT
    }

    public static class ConnectionInfo {
        public final String identifier; // address or endpointId
        public final String deviceName;
        public final TransportType transport;
        public long lastSeen;
        public long connectedAt;
        public int messagesSent;
        public int messagesReceived;
        public long totalLatency; // sum of all latencies
        public int latencySamples;
        public int failedAttempts;
        public int rssi; // signal strength (for BLE/WiFi)

        public ConnectionInfo(String identifier, String deviceName, TransportType transport) {
            this.identifier = identifier;
            this.deviceName = deviceName;
            this.transport = transport;
            this.connectedAt = System.currentTimeMillis();
            this.lastSeen = this.connectedAt;
            this.rssi = -100; // unknown/weak
        }

        public long getAverageLatency() {
            return latencySamples > 0 ? totalLatency / latencySamples : -1;
        }

        public double getQualityScore() {
            // Higher is better
            // Factors: latency (40%), reliability (30%), signal (20%), recency (10%)
            double latencyScore = 0;
            long avgLatency = getAverageLatency();
            if (avgLatency > 0) {
                latencyScore = Math.max(0, 100 - (avgLatency / 10.0));
            }

            double reliabilityScore = 0;
            int totalAttempts = messagesSent + failedAttempts;
            if (totalAttempts > 0) {
                reliabilityScore = (messagesSent * 100.0) / totalAttempts;
            }

            double signalScore = Math.max(0, 100 + rssi); // rssi is negative

            long ageMs = System.currentTimeMillis() - lastSeen;
            double recencyScore = Math.max(0, 100 - (ageMs / 100.0));

            return (latencyScore * 0.4) + (reliabilityScore * 0.3) +
                    (signalScore * 0.2) + (recencyScore * 0.1);
        }

        public boolean isStale() {
            return (System.currentTimeMillis() - lastSeen) > 30000; // 30s
        }
    }

    private final Map<String, ConnectionInfo> connections = new ConcurrentHashMap<>();
    private final Map<String, List<String>> deviceToTransports = new HashMap<>(); // deviceId -> [identifiers]

    public void addConnection(String identifier, String deviceName, TransportType transport) {
        ConnectionInfo info = new ConnectionInfo(identifier, deviceName, transport);
        connections.put(identifier, info);

        // Track multiple transports for same device
        synchronized (deviceToTransports) {
            List<String> transports = deviceToTransports.get(deviceName);
            if (transports == null) {
                transports = new ArrayList<>();
                deviceToTransports.put(deviceName, transports);
            }
            if (!transports.contains(identifier)) {
                transports.add(identifier);
            }
        }

        Log.d(TAG, "Added connection: " + identifier + " (" + transport + ") - " + deviceName);
    }

    public void removeConnection(String identifier) {
        ConnectionInfo info = connections.remove(identifier);
        if (info != null) {
            synchronized (deviceToTransports) {
                List<String> transports = deviceToTransports.get(info.deviceName);
                if (transports != null) {
                    transports.remove(identifier);
                    if (transports.isEmpty()) {
                        deviceToTransports.remove(info.deviceName);
                    }
                }
            }
            Log.d(TAG, "Removed connection: " + identifier);
        }
    }

    public void updateLastSeen(String identifier) {
        ConnectionInfo info = connections.get(identifier);
        if (info != null) {
            info.lastSeen = System.currentTimeMillis();
        }
    }

    public void recordMessageSent(String identifier, long latencyMs) {
        ConnectionInfo info = connections.get(identifier);
        if (info != null) {
            info.messagesSent++;
            if (latencyMs > 0) {
                info.totalLatency += latencyMs;
                info.latencySamples++;
            }
            info.lastSeen = System.currentTimeMillis();
        }
    }

    public void recordMessageReceived(String identifier) {
        ConnectionInfo info = connections.get(identifier);
        if (info != null) {
            info.messagesReceived++;
            info.lastSeen = System.currentTimeMillis();
        }
    }

    public void recordFailure(String identifier) {
        ConnectionInfo info = connections.get(identifier);
        if (info != null) {
            info.failedAttempts++;
        }
    }

    public void updateRSSI(String identifier, int rssi) {
        ConnectionInfo info = connections.get(identifier);
        if (info != null) {
            info.rssi = rssi;
        }
    }

    /**
     * Get best connection for a specific device
     * Returns the identifier of the best transport to use
     */
    public String getBestConnectionForDevice(String deviceName) {
        synchronized (deviceToTransports) {
            List<String> transports = deviceToTransports.get(deviceName);
            if (transports == null || transports.isEmpty()) {
                return null;
            }

            // Find highest quality connection
            String best = null;
            double bestScore = -1;

            for (String identifier : transports) {
                ConnectionInfo info = connections.get(identifier);
                if (info != null && !info.isStale()) {
                    double score = info.getQualityScore();
                    if (score > bestScore) {
                        bestScore = score;
                        best = identifier;
                    }
                }
            }

            return best;
        }
    }

    /**
     * Get all active connections
     */
    public List<ConnectionInfo> getAllConnections() {
        return new ArrayList<>(connections.values());
    }

    /**
     * Get connections by transport type
     */
    public List<ConnectionInfo> getConnectionsByTransport(TransportType transport) {
        List<ConnectionInfo> result = new ArrayList<>();
        for (ConnectionInfo info : connections.values()) {
            if (info.transport == transport && !info.isStale()) {
                result.add(info);
            }
        }
        return result;
    }

    /**
     * Clean up stale connections
     */
    public void cleanupStaleConnections() {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ConnectionInfo> entry : connections.entrySet()) {
            if (entry.getValue().isStale()) {
                toRemove.add(entry.getKey());
            }
        }

        for (String identifier : toRemove) {
            removeConnection(identifier);
            Log.d(TAG, "Cleaned up stale connection: " + identifier);
        }
    }

    /**
     * Get connection info
     */
    public ConnectionInfo getConnectionInfo(String identifier) {
        return connections.get(identifier);
    }

    /**
     * Check if device is connected via any transport
     */
    public boolean isDeviceConnected(String deviceName) {
        synchronized (deviceToTransports) {
            List<String> transports = deviceToTransports.get(deviceName);
            if (transports == null || transports.isEmpty()) {
                return false;
            }

            // Check if any transport is active
            for (String identifier : transports) {
                ConnectionInfo info = connections.get(identifier);
                if (info != null && !info.isStale()) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Get summary statistics
     */
    public String getStatsSummary() {
        int total = connections.size();
        int bt = getConnectionsByTransport(TransportType.BLUETOOTH_CLASSIC).size();
        int ble = getConnectionsByTransport(TransportType.BLUETOOTH_LE).size();
        int nearby = getConnectionsByTransport(TransportType.NEARBY_WIFI_DIRECT).size();

        return String.format("Connections: %d (BT:%d, BLE:%d, Nearby:%d)", total, bt, ble, nearby);
    }
}
