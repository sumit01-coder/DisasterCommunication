package com.example.disastercomm.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.disastercomm.models.Message;

import java.util.HashMap;
import java.util.Map;

/**
 * Monitors mesh network health with heartbeats, dead node detection, and
 * self-healing.
 */
public class NetworkHealthMonitor {
    private static final String TAG = "NetworkHealth";
    private static final long HEARTBEAT_INTERVAL_MS = 30 * 1000; // 30 seconds
    private static final long DEAD_NODE_THRESHOLD_MS = 2 * 60 * 1000; // 2 minutes
    private static final long CLEANUP_INTERVAL_MS = 60 * 1000; // 1 minute

    private final Context context;
    private final MeshRoutingTable routingTable;
    private final HealthCallback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Map<String, Long> lastHeartbeats = new HashMap<>();
    private final NetworkStats stats = new NetworkStats();

    private boolean isMonitoring = false;
    private String myDeviceId;
    private int myBatteryLevel = 100;

    public interface HealthCallback {
        void sendHeartbeat(Message heartbeat);

        void onNodeDead(String deviceId);

        int getBatteryLevel();
    }

    public static class NetworkStats {
        public int totalMessages = 0;
        public int deliveredMessages = 0;
        public int failedMessages = 0;
        public long totalLatency = 0; // ms
        public int maxHops = 0;
        public long uptime = 0;

        public float getDeliveryRate() {
            if (totalMessages == 0)
                return 0;
            return (float) deliveredMessages / totalMessages * 100;
        }

        public float getAverageLatency() {
            if (deliveredMessages == 0)
                return 0;
            return (float) totalLatency / deliveredMessages;
        }
    }

    private final Runnable heartbeatTask = new Runnable() {
        @Override
        public void run() {
            if (isMonitoring) {
                sendHeartbeat();
                handler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        }
    };

    private final Runnable cleanupTask = new Runnable() {
        @Override
        public void run() {
            if (isMonitoring) {
                checkDeadNodes();
                handler.postDelayed(this, CLEANUP_INTERVAL_MS);
            }
        }
    };

    public NetworkHealthMonitor(Context context, String myDeviceId, MeshRoutingTable routingTable,
            HealthCallback callback) {
        this.context = context;
        this.myDeviceId = myDeviceId;
        this.routingTable = routingTable;
        this.callback = callback;
    }

    /**
     * Start health monitoring
     */
    public void startMonitoring() {
        if (isMonitoring)
            return;

        isMonitoring = true;
        stats.uptime = System.currentTimeMillis();

        handler.post(heartbeatTask);
        handler.postDelayed(cleanupTask, CLEANUP_INTERVAL_MS);

        Log.d(TAG, "❤️ Network health monitoring started");
    }

    /**
     * Stop monitoring
     */
    public void stopMonitoring() {
        isMonitoring = false;
        handler.removeCallbacks(heartbeatTask);
        handler.removeCallbacks(cleanupTask);

        Log.d(TAG, "⏸️ Network health monitoring stopped");
    }

    /**
     * Send periodic heartbeat
     */
    private void sendHeartbeat() {
        myBatteryLevel = callback.getBatteryLevel();
        int neighborCount = routingTable.getNeighbors().size();

        Message heartbeat = new Message();
        heartbeat.id = myDeviceId + "_HB_" + System.currentTimeMillis();
        heartbeat.senderId = myDeviceId;
        heartbeat.receiverId = "ALL";
        heartbeat.type = Message.Type.HEARTBEAT;
        heartbeat.content = myBatteryLevel + "," + neighborCount;
        heartbeat.hopCount = 0;
        heartbeat.maxHops = 2; // Limited propagation
        heartbeat.timestamp = System.currentTimeMillis();

        callback.sendHeartbeat(heartbeat);

        Log.d(TAG, String.format("💓 Sent heartbeat: Battery %d%%, Neighbors %d",
                myBatteryLevel, neighborCount));
    }

    /**
     * Handle received heartbeat
     */
    public void handleHeartbeat(Message heartbeat) {
        String senderId = heartbeat.senderId;
        lastHeartbeats.put(senderId, System.currentTimeMillis());

        // Parse battery and neighbor count
        try {
            String[] parts = heartbeat.content.split(",");
            if (parts.length >= 1) {
                int battery = Integer.parseInt(parts[0]);
                routingTable.updateNeighborBattery(senderId, battery);
            }
        } catch (Exception e) {
            // Ignore parse errors
        }

        Log.d(TAG, "💓 Received heartbeat from " + senderId.substring(0, 8));
    }

    /**
     * Check for dead nodes
     */
    private void checkDeadNodes() {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : lastHeartbeats.entrySet()) {
            long timeSinceLastHeartbeat = now - entry.getValue();

            if (timeSinceLastHeartbeat > DEAD_NODE_THRESHOLD_MS) {
                String deadNodeId = entry.getKey();
                Log.w(TAG, "💀 Dead node detected: " + deadNodeId.substring(0, 8) +
                        " (no heartbeat for " + (timeSinceLastHeartbeat / 1000) + "s)");

                lastHeartbeats.remove(deadNodeId);
                callback.onNodeDead(deadNodeId);
            }
        }

        // Also cleanup routing table
        routingTable.cleanup();
    }

    /**
     * Record message statistics
     */
    public void recordMessageSent() {
        stats.totalMessages++;
    }

    public void recordMessageDelivered(long latencyMs, int hopCount) {
        stats.deliveredMessages++;
        stats.totalLatency += latencyMs;
        if (hopCount > stats.maxHops) {
            stats.maxHops = hopCount;
        }
    }

    public void recordMessageFailed() {
        stats.failedMessages++;
    }

    /**
     * Get network statistics
     */
    public NetworkStats getStats() {
        stats.uptime = System.currentTimeMillis() - stats.uptime;
        return stats;
    }

    /**
     * Get health report string
     */
    public String getHealthReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== NETWORK HEALTH REPORT ===\n\n");

        // Overall stats
        report.append(String.format("Uptime: %d minutes\n", stats.uptime / 1000 / 60));
        report.append(String.format("Messages: %d sent, %d delivered, %d failed\n",
                stats.totalMessages, stats.deliveredMessages, stats.failedMessages));
        report.append(String.format("Delivery rate: %.1f%%\n", stats.getDeliveryRate()));
        report.append(String.format("Average latency: %.0f ms\n", stats.getAverageLatency()));
        report.append(String.format("Max hops: %d\n\n", stats.maxHops));

        // Routing table stats
        MeshRoutingTable.NetworkStats routingStats = routingTable.getStats();
        report.append(String.format("Active neighbors: %d\n", routingStats.neighborCount));
        report.append(String.format("Known routes: %d\n", routingStats.routeCount));
        report.append(String.format("Average hops: %.1f\n", routingStats.averageHops));
        report.append(String.format("Network diameter: %d hops\n", routingStats.networkDiameter));
        report.append(String.format("Active relays: %d\n", routingStats.relayCount));

        return report.toString();
    }

    /**
     * Reset statistics
     */
    public void resetStats() {
        stats.totalMessages = 0;
        stats.deliveredMessages = 0;
        stats.failedMessages = 0;
        stats.totalLatency = 0;
        stats.maxHops = 0;
        stats.uptime = System.currentTimeMillis();

        Log.d(TAG, "♻️ Statistics reset");
    }
}
