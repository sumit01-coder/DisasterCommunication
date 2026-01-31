package com.example.disastercomm.network;

import android.util.Log;

import com.example.disastercomm.models.Message;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implements AODV-style route discovery protocol for mesh networking.
 * Uses RREQ (Route Request), RREP (Route Reply), and RERR (Route Error)
 * messages.
 */
public class RouteDiscoveryProtocol {
    private static final String TAG = "RouteDiscovery";
    private static final long RREQ_LIFETIME_MS = 10000; // 10 seconds
    private static final int MAX_RREQ_RETRIES = 3;

    private final MeshRoutingTable routingTable;
    private final String myDeviceId;
    private final RouteDiscoveryCallback callback;

    // Track sent RREQs to prevent duplicates
    private final Map<String, RREQRecord> pendingRequests = new HashMap<>();

    // Track seen RREQs to prevent broadcast storms
    private final Set<String> seenRREQs = new HashSet<>();

    private int requestSequence = 0;

    public interface RouteDiscoveryCallback {
        void onRouteFound(String destinationId, String nextHop, int hopCount);

        void onRouteError(String destinationId, String brokenLink);

        void sendMessage(Message message);
    }

    private static class RREQRecord {
        String destinationId;
        int sequenceNumber;
        long timestamp;
        int retryCount;

        RREQRecord(String destId, int seqNum) {
            this.destinationId = destId;
            this.sequenceNumber = seqNum;
            this.timestamp = System.currentTimeMillis();
            this.retryCount = 0;
        }

        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > RREQ_LIFETIME_MS;
        }
    }

    public RouteDiscoveryProtocol(String myDeviceId, MeshRoutingTable routingTable, RouteDiscoveryCallback callback) {
        this.myDeviceId = myDeviceId;
        this.routingTable = routingTable;
        this.callback = callback;
    }

    /**
     * Initiate route discovery to a destination
     */
    public void discoverRoute(String destinationId) {
        // Check if we already have a valid route
        if (routingTable.hasRoute(destinationId)) {
            Log.d(TAG, "✅ Route already exists to " + destinationId.substring(0, 8));
            return;
        }

        // Check if we already have a pending request
        RREQRecord existing = pendingRequests.get(destinationId);
        if (existing != null && !existing.isExpired()) {
            Log.d(TAG, "⏳ Route discovery already in progress for " + destinationId.substring(0, 8));
            return;
        }

        // Create and broadcast RREQ
        requestSequence++;
        RREQRecord record = new RREQRecord(destinationId, requestSequence);
        pendingRequests.put(destinationId, record);

        Message rreq = new Message();
        rreq.id = myDeviceId + "_RREQ_" + requestSequence;
        rreq.senderId = myDeviceId;
        rreq.receiverId = "ALL"; // Broadcast
        rreq.type = Message.Type.ROUTE_REQUEST;
        rreq.originatorId = myDeviceId;
        rreq.content = destinationId; // Target destination
        rreq.hopCount = 0;
        rreq.maxHops = 10;
        rreq.routeSequence = requestSequence;
        rreq.routePath = myDeviceId;
        rreq.timestamp = System.currentTimeMillis();

        Log.d(TAG, String.format("📡 Broadcasting RREQ for %s (seq: %d)",
                destinationId.substring(0, 8), requestSequence));

        callback.sendMessage(rreq);
    }

    /**
     * Handle received RREQ message
     */
    public void handleRouteRequest(Message rreq) {
        String rreqId = rreq.originatorId + "_" + rreq.routeSequence;

        // Check if we've already seen this RREQ (prevent loops)
        if (seenRREQs.contains(rreqId)) {
            Log.d(TAG, "🔁 Duplicate RREQ ignored: " + rreqId);
            return;
        }
        seenRREQs.add(rreqId);

        // Check TTL
        if (rreq.hopCount >= rreq.maxHops) {
            Log.d(TAG, "⏰ RREQ exceeded max hops, dropping");
            return;
        }

        String destinationId = rreq.content;
        String originatorId = rreq.originatorId;

        // Update route to originator (reverse path)
        routingTable.addRoute(originatorId, rreq.senderId, rreq.hopCount + 1, -50);

        // Are we the destination?
        if (destinationId.equals(myDeviceId)) {
            Log.d(TAG, "🎯 RREQ reached destination! Sending RREP back to " + originatorId.substring(0, 8));
            sendRouteReply(originatorId, rreq);
            return;
        }

        // Do we have a route to destination?
        if (routingTable.hasRoute(destinationId)) {
            // We can send RREP on behalf of destination (intermediate RREP)
            Log.d(TAG, "🔀 Intermediate node has route to " + destinationId.substring(0, 8));
            sendRouteReply(originatorId, rreq);
            return;
        }

        // Forward RREQ
        Message forwardedRREQ = new Message();
        forwardedRREQ.id = rreq.id;
        forwardedRREQ.senderId = myDeviceId;
        forwardedRREQ.receiverId = "ALL";
        forwardedRREQ.type = Message.Type.ROUTE_REQUEST;
        forwardedRREQ.originatorId = originatorId;
        forwardedRREQ.content = destinationId;
        forwardedRREQ.hopCount = rreq.hopCount + 1;
        forwardedRREQ.maxHops = rreq.maxHops;
        forwardedRREQ.routeSequence = rreq.routeSequence;
        forwardedRREQ.routePath = rreq.routePath + "→" + myDeviceId;
        forwardedRREQ.timestamp = rreq.timestamp;

        Log.d(TAG, String.format("⏩ Forwarding RREQ (hop %d/%d): %s",
                forwardedRREQ.hopCount, forwardedRREQ.maxHops, forwardedRREQ.routePath));

        callback.sendMessage(forwardedRREQ);
    }

    /**
     * Send RREP back to originator
     */
    private void sendRouteReply(String originatorId, Message rreq) {
        String destinationId = rreq.content;
        int hopCountToDestination = routingTable.getHopCount(destinationId);
        if (hopCountToDestination < 0) {
            hopCountToDestination = 0; // We are the destination
        }

        Message rrep = new Message();
        rrep.id = myDeviceId + "_RREP_" + System.currentTimeMillis();
        rrep.senderId = myDeviceId;
        rrep.receiverId = originatorId; // Unicast back to originator
        rrep.type = Message.Type.ROUTE_REPLY;
        rrep.content = destinationId; // Which destination this reply is for
        rrep.hopCount = 0;
        rrep.maxHops = rreq.maxHops;
        rrep.routeSequence = rreq.routeSequence;
        rrep.originatorId = destinationId;
        rrep.routePath = myDeviceId;
        rrep.timestamp = System.currentTimeMillis();

        // Get next hop back to originator
        String nextHop = routingTable.getNextHop(originatorId);
        if (nextHop != null) {
            rrep.nextHop = nextHop;
            Log.d(TAG, String.format("📨 Sending RREP to %s via %s",
                    originatorId.substring(0, 8), nextHop.substring(0, 8)));
            callback.sendMessage(rrep);
        } else {
            Log.w(TAG, "❌ Cannot send RREP - no route back to originator!");
        }
    }

    /**
     * Handle received RREP message
     */
    public void handleRouteReply(Message rrep) {
        String destinationId = rrep.content;
        String originatorId = rrep.originatorId;

        // Update route to destination
        routingTable.addRoute(destinationId, rrep.senderId, rrep.hopCount + 1, -50);

        // Update route to originator (source of RREP)
        routingTable.addRoute(originatorId, rrep.senderId, rrep.hopCount + 1, -50);

        Log.d(TAG, String.format("✅ Route learned: %s via %s (%d hops)",
                destinationId.substring(0, 8), rrep.senderId.substring(0, 8), rrep.hopCount + 1));

        // Are we the original requester?
        if (rrep.receiverId.equals(myDeviceId)) {
            Log.d(TAG, "🎯 RREP reached us! Route to " + destinationId.substring(0, 8) + " established");
            pendingRequests.remove(destinationId);
            callback.onRouteFound(destinationId, rrep.senderId, rrep.hopCount + 1);
            return;
        }

        // Forward RREP toward originator
        String nextHop = routingTable.getNextHop(rrep.receiverId);
        if (nextHop != null) {
            Message forwardedRREP = new Message();
            forwardedRREP.id = rrep.id;
            forwardedRREP.senderId = myDeviceId;
            forwardedRREP.receiverId = rrep.receiverId;
            forwardedRREP.type = Message.Type.ROUTE_REPLY;
            forwardedRREP.content = destinationId;
            forwardedRREP.hopCount = rrep.hopCount + 1;
            forwardedRREP.maxHops = rrep.maxHops;
            forwardedRREP.routeSequence = rrep.routeSequence;
            forwardedRREP.originatorId = originatorId;
            forwardedRREP.routePath = rrep.routePath + "→" + myDeviceId;
            forwardedRREP.nextHop = nextHop;
            forwardedRREP.timestamp = rrep.timestamp;

            Log.d(TAG, "⏩ Forwarding RREP toward " + rrep.receiverId.substring(0, 8));
            callback.sendMessage(forwardedRREP);
        }
    }

    /**
     * Handle route error (link breakage)
     */
    public void handleRouteError(Message rerr) {
        String brokenLink = rerr.content; // Format: "nodeA→nodeB"
        Log.w(TAG, "💔 Route error received: " + brokenLink);

        // Invalidate affected routes
        String[] parts = brokenLink.split("→");
        if (parts.length == 2) {
            String nodeA = parts[0];
            String nodeB = parts[1];

            // Remove routes that use this broken link
            callback.onRouteError(nodeB, nodeA);
        }
    }

    /**
     * Send route error when a link breaks
     */
    public void reportLinkBreakage(String brokenNodeId) {
        Message rerr = new Message();
        rerr.id = myDeviceId + "_RERR_" + System.currentTimeMillis();
        rerr.senderId = myDeviceId;
        rerr.receiverId = "ALL"; // Broadcast to affected nodes
        rerr.type = Message.Type.ROUTE_ERROR;
        rerr.content = myDeviceId + "→" + brokenNodeId;
        rerr.hopCount = 0;
        rerr.maxHops = 5; // Limited propagation
        rerr.timestamp = System.currentTimeMillis();

        Log.w(TAG, "📢 Broadcasting RERR for broken link: " + rerr.content);
        callback.sendMessage(rerr);
    }

    /**
     * Clean up expired RREQs
     */
    public void cleanup() {
        pendingRequests.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired()) {
                Log.d(TAG, "⏰ RREQ expired for " + entry.getKey().substring(0, 8));
                return true;
            }
            return false;
        });

        // Clean old seen RREQs (keep last 100)
        if (seenRREQs.size() > 100) {
            seenRREQs.clear();
            Log.d(TAG, "♻️ Cleared RREQ cache");
        }
    }

    /**
     * Get pending request count
     */
    public int getPendingRequestCount() {
        return pendingRequests.size();
    }
}
