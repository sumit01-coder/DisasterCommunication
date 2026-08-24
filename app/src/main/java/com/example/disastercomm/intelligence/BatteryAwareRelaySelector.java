package com.example.disastercomm.intelligence;

import com.example.disastercomm.network.MeshRoutingTable;
import java.util.Map;

public class BatteryAwareRelaySelector {
    
    // Nodes below this battery level will not be used as intermediate relays
    public static final int CRITICAL_BATTERY_THRESHOLD = 15;

    /**
     * Checks if a neighbor is healthy enough to be used as a relay.
     * 
     * @param neighbor The neighbor info from the routing table.
     * @return true if the neighbor can be used as a relay.
     */
    public static boolean isHealthyRelay(MeshRoutingTable.NeighborInfo neighbor) {
        if (neighbor == null) return false;
        if (!neighbor.isAlive()) return false;
        
        // Critical Rule: Never use device with <15% battery as relay
        if (neighbor.batteryLevel < CRITICAL_BATTERY_THRESHOLD) {
            return false;
        }

        return true;
    }

    /**
     * Filters a proposed route based on battery awareness.
     * If the next hop is a low-battery device, and it is NOT the final destination,
     * the route should be rejected to save the dying device's battery.
     */
    public static boolean isRouteAllowed(String destinationId, String nextHop, Map<String, MeshRoutingTable.NeighborInfo> neighbors) {
        // If the next hop IS the destination, we must route to it regardless of battery
        if (destinationId.equals(nextHop)) {
            return true;
        }

        // If it's a relay, check battery health
        MeshRoutingTable.NeighborInfo nextHopNeighbor = neighbors.get(nextHop);
        return isHealthyRelay(nextHopNeighbor);
    }
}
