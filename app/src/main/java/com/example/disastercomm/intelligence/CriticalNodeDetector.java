package com.example.disastercomm.intelligence;

import com.example.disastercomm.network.MeshRoutingTable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class CriticalNodeDetector {

    /**
     * Identifies nodes that are single points of failure (cut vertices).
     * If these nodes go down, the network splits into isolated islands.
     *
     * @param neighbors Direct neighbors
     * @param routes All known routes
     * @return List of critical node IDs
     */
    public static List<String> detectCriticalNodes(Map<String, MeshRoutingTable.NeighborInfo> neighbors, Map<String, MeshRoutingTable.RouteInfo> routes) {
        List<String> criticalNodes = new ArrayList<>();
        
        // This is a simplified critical node heuristic based on routing table dependencies.
        // A node is considered critical if multiple disparate routes MUST pass through it.
        
        Map<String, Integer> dependencyCount = new HashMap<>();
        
        for (MeshRoutingTable.RouteInfo route : routes.values()) {
            if (route.hopCount > 1 && route.nextHop != null) {
                dependencyCount.put(route.nextHop, dependencyCount.getOrDefault(route.nextHop, 0) + 1);
            }
        }
        
        // If a direct neighbor is the *only* way to reach more than 3 other devices, 
        // it's highly critical.
        for (Map.Entry<String, Integer> entry : dependencyCount.entrySet()) {
            if (entry.getValue() >= 3) {
                criticalNodes.add(entry.getKey());
            }
        }
        
        return criticalNodes;
    }
}
