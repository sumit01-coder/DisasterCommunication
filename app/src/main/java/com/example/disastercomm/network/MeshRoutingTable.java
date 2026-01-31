package com.example.disastercomm.network;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages mesh network routing table for multi-hop message delivery.
 * Tracks network topology, neighbors, and optimal routes to destinations.
 */
public class MeshRoutingTable {
    private static final String TAG = "MeshRoutingTable";
    private static final long ROUTE_TIMEOUT_MS = 5 * 60 * 1000; // 5 minutes
    private static final long NEIGHBOR_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes

    // Route entry: destination -> RouteInfo
    private final Map<String, RouteInfo> routeTable = new ConcurrentHashMap<>();

    // Direct neighbors: deviceId -> NeighborInfo
    private final Map<String, NeighborInfo> neighbors = new ConcurrentHashMap<>();

    // Sequence number for route freshness
    private int sequenceNumber = 0;

    public static class RouteInfo {
        public String destinationId;
        public String nextHop; // Next device to forward to
        public int hopCount; // Number of hops to destination
        public int signalStrength; // RSSI if available
        public long lastUpdated; // Timestamp
        public int sequenceNumber; // For route freshness
        public String[] fullPath; // Complete path if known

        public RouteInfo(String destinationId, String nextHop, int hopCount) {
            this.destinationId = destinationId;
            this.nextHop = nextHop;
            this.hopCount = hopCount;
            this.lastUpdated = System.currentTimeMillis();
            this.signalStrength = -999;
        }

        public boolean isExpired() {
            return (System.currentTimeMillis() - lastUpdated) > ROUTE_TIMEOUT_MS;
        }
    }

    public static class NeighborInfo {
        public String deviceId;
        public String deviceName;
        public int batteryLevel; // 0-100
        public int signalStrength; // RSSI
        public long lastSeen; // Timestamp of last heartbeat
        public boolean isRelay; // Is this device in relay mode?

        public NeighborInfo(String deviceId, String deviceName) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.lastSeen = System.currentTimeMillis();
            this.batteryLevel = 100;
            this.signalStrength = -50;
        }

        public boolean isAlive() {
            return (System.currentTimeMillis() - lastSeen) < NEIGHBOR_TIMEOUT_MS;
        }
    }

    /**
     * Add or update a direct neighbor
     */
    public void addNeighbor(String deviceId, String deviceName) {
        NeighborInfo neighbor = neighbors.get(deviceId);
        if (neighbor == null) {
            neighbor = new NeighborInfo(deviceId, deviceName);
            neighbors.put(deviceId, neighbor);
            Log.d(TAG, "✅ New neighbor added: " + deviceName + " (" + deviceId.substring(0, 8) + ")");
        } else {
            neighbor.lastSeen = System.currentTimeMillis();
        }

        // Direct neighbors have 1-hop route
        addRoute(deviceId, deviceId, 1, -50);
    }

    /**
     * Remove a neighbor (disconnected)
     */
    public void removeNeighbor(String deviceId) {
        NeighborInfo removed = neighbors.remove(deviceId);
        if (removed != null) {
            Log.d(TAG, "❌ Neighbor removed: " + removed.deviceName);

            // Remove direct route
            routeTable.remove(deviceId);

            // Remove routes that go through this neighbor
            invalidateRoutesThrough(deviceId);
        }
    }

    /**
     * Add or update a route to a destination
     */
    public void addRoute(String destinationId, String nextHop, int hopCount, int signalStrength) {
        RouteInfo existing = routeTable.get(destinationId);

        // Only update if:
        // 1. No existing route, OR
        // 2. New route has fewer hops, OR
        // 3. Same hops but better signal
        boolean shouldUpdate = existing == null ||
                hopCount < existing.hopCount ||
                (hopCount == existing.hopCount && signalStrength > existing.signalStrength);

        if (shouldUpdate) {
            RouteInfo route = new RouteInfo(destinationId, nextHop, hopCount);
            route.signalStrength = signalStrength;
            route.sequenceNumber = sequenceNumber++;
            routeTable.put(destinationId, route);

            Log.d(TAG, String.format("🔄 Route updated: %s → %s (%d hops, signal: %d)",
                    destinationId.substring(0, 8), nextHop.substring(0, 8), hopCount, signalStrength));
        }
    }

    /**
     * Get next hop for a destination (null if no route)
     */
    public String getNextHop(String destinationId) {
        // Check if it's a direct neighbor first
        if (neighbors.containsKey(destinationId)) {
            return destinationId;
        }

        RouteInfo route = routeTable.get(destinationId);
        if (route != null && !route.isExpired()) {
            return route.nextHop;
        }

        return null; // No route available
    }

    /**
     * Check if we have a valid route to destination
     */
    public boolean hasRoute(String destinationId) {
        if (neighbors.containsKey(destinationId)) {
            return true;
        }

        RouteInfo route = routeTable.get(destinationId);
        return route != null && !route.isExpired();
    }

    /**
     * Check if device is a direct neighbor
     */
    public boolean isNeighbor(String deviceId) {
        NeighborInfo neighbor = neighbors.get(deviceId);
        return neighbor != null && neighbor.isAlive();
    }

    /**
     * Get hop count to destination (-1 if no route)
     */
    public int getHopCount(String destinationId) {
        if (neighbors.containsKey(destinationId)) {
            return 1;
        }

        RouteInfo route = routeTable.get(destinationId);
        if (route != null && !route.isExpired()) {
            return route.hopCount;
        }

        return -1;
    }

    /**
     * Remove routes that use a specific device as next hop
     */
    private void invalidateRoutesThrough(String nextHopId) {
        int removed = 0;
        for (Map.Entry<String, RouteInfo> entry : routeTable.entrySet()) {
            if (entry.getValue().nextHop.equals(nextHopId)) {
                routeTable.remove(entry.getKey());
                removed++;
            }
        }

        if (removed > 0) {
            Log.d(TAG, "♻️ Invalidated " + removed + " routes through " + nextHopId.substring(0, 8));
        }
    }

    /**
     * Clean up expired routes and dead neighbors
     */
    public void cleanup() {
        // Remove dead neighbors
        neighbors.entrySet().removeIf(entry -> {
            if (!entry.getValue().isAlive()) {
                Log.d(TAG, "💀 Dead neighbor removed: " + entry.getValue().deviceName);
                invalidateRoutesThrough(entry.getKey());
                return true;
            }
            return false;
        });

        // Remove expired routes
        routeTable.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                Log.d(TAG, "⏰ Expired route removed: " + entry.getKey().substring(0, 8));
                return true;
            }
            return false;
        });
    }

    /**
     * Update neighbor battery level (from heartbeat)
     */
    public void updateNeighborBattery(String deviceId, int batteryLevel) {
        NeighborInfo neighbor = neighbors.get(deviceId);
        if (neighbor != null) {
            neighbor.batteryLevel = batteryLevel;
            neighbor.lastSeen = System.currentTimeMillis();
        }
    }

    /**
     * Update neighbor signal strength
     */
    public void updateNeighborSignal(String deviceId, int rssi) {
        NeighborInfo neighbor = neighbors.get(deviceId);
        if (neighbor != null) {
            neighbor.signalStrength = rssi;
        }
    }

    /**
     * Mark neighbor as relay
     */
    public void setNeighborRelay(String deviceId, boolean isRelay) {
        NeighborInfo neighbor = neighbors.get(deviceId);
        if (neighbor != null) {
            neighbor.isRelay = isRelay;
        }
    }

    /**
     * Get all direct neighbors
     */
    public Map<String, NeighborInfo> getNeighbors() {
        return new HashMap<>(neighbors);
    }

    /**
     * Get all routes
     */
    public Map<String, RouteInfo> getRoutes() {
        return new HashMap<>(routeTable);
    }

    /**
     * Get network statistics
     */
    public NetworkStats getStats() {
        NetworkStats stats = new NetworkStats();
        stats.neighborCount = neighbors.size();
        stats.routeCount = routeTable.size();

        int totalHops = 0;
        int maxHops = 0;
        for (RouteInfo route : routeTable.values()) {
            totalHops += route.hopCount;
            if (route.hopCount > maxHops) {
                maxHops = route.hopCount;
            }
        }

        stats.averageHops = routeTable.isEmpty() ? 0 : (float) totalHops / routeTable.size();
        stats.networkDiameter = maxHops;
        stats.relayCount = (int) neighbors.values().stream().filter(n -> n.isRelay).count();

        return stats;
    }

    public static class NetworkStats {
        public int neighborCount; // Direct connections
        public int routeCount; // Total known routes
        public float averageHops; // Average path length
        public int networkDiameter; // Max hops (network size)
        public int relayCount; // Active relays
    }

    /**
     * Clear all routes and neighbors (for testing/reset)
     */
    public void clear() {
        routeTable.clear();
        neighbors.clear();
        sequenceNumber = 0;
        Log.d(TAG, "🔄 Routing table cleared");
    }

    /**
     * Get debug string for logging
     */
    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MESH ROUTING TABLE ===\n");
        sb.append("Neighbors (").append(neighbors.size()).append("):\n");
        for (NeighborInfo n : neighbors.values()) {
            sb.append(String.format("  • %s - Battery: %d%%, RSSI: %d%s\n",
                    n.deviceName, n.batteryLevel, n.signalStrength,
                    n.isRelay ? " [RELAY]" : ""));
        }

        sb.append("\nRoutes (").append(routeTable.size()).append("):\n");
        for (RouteInfo r : routeTable.values()) {
            sb.append(String.format("  • %s via %s (%d hops)\n",
                    r.destinationId.substring(0, 8),
                    r.nextHop.substring(0, 8),
                    r.hopCount));
        }

        return sb.toString();
    }
}
