package com.example.disastercomm;

import org.osmdroid.util.GeoPoint;
import java.util.HashMap;
import java.util.Map;

/**
 * Manager to track peer locations and live sharing status
 */
public class PeerLocationManager {
    private static PeerLocationManager instance;
    private final Map<String, GeoPoint> peerLocations = new HashMap<>();
    private final Map<String, Boolean> liveShareStatus = new HashMap<>();
    private final Map<String, Long> sharingUntilTimestamp = new HashMap<>();
    private final Map<String, Long> lastUpdateTimestamp = new HashMap<>();

    private PeerLocationManager() {
    }

    public static synchronized PeerLocationManager getInstance() {
        if (instance == null) {
            instance = new PeerLocationManager();
        }
        return instance;
    }

    /**
     * Update peer location with optional live sharing metadata
     */
    public void updatePeerLocation(String endpointId, double lat, double lng) {
        updatePeerLocation(endpointId, lat, lng, false, 0);
    }

    /**
     * Update peer location with live sharing metadata
     */
    public void updatePeerLocation(String endpointId, double lat, double lng,
            boolean isLiveSharing, long sharingUntil) {
        peerLocations.put(endpointId, new GeoPoint(lat, lng));
        liveShareStatus.put(endpointId, isLiveSharing);
        sharingUntilTimestamp.put(endpointId, sharingUntil);
        lastUpdateTimestamp.put(endpointId, System.currentTimeMillis());
    }

    public Map<String, GeoPoint> getPeerLocations() {
        return new HashMap<>(peerLocations);
    }

    public GeoPoint getPeerLocation(String endpointId) {
        return peerLocations.get(endpointId);
    }

    /**
     * Check if peer is actively sharing live location
     */
    public boolean isPeerLiveSharing(String endpointId) {
        Boolean status = liveShareStatus.get(endpointId);
        if (status == null || !status) {
            return false;
        }

        // Check if sharing has expired
        Long until = sharingUntilTimestamp.get(endpointId);
        if (until != null && until != Long.MAX_VALUE && System.currentTimeMillis() > until) {
            liveShareStatus.put(endpointId, false);
            return false;
        }

        return true;
    }

    /**
     * Get the timestamp when peer's sharing will end
     */
    public long getPeerSharingUntil(String endpointId) {
        Long until = sharingUntilTimestamp.get(endpointId);
        return until != null ? until : 0;
    }

    /**
     * Get timestamp of last location update for peer
     */
    public long getLastUpdateTime(String endpointId) {
        Long time = lastUpdateTimestamp.get(endpointId);
        return time != null ? time : 0;
    }

    public void removePeer(String endpointId) {
        peerLocations.remove(endpointId);
        liveShareStatus.remove(endpointId);
        sharingUntilTimestamp.remove(endpointId);
        lastUpdateTimestamp.remove(endpointId);
    }

    /**
     * Clean up expired live sharing status
     */
    public void cleanupExpiredSharing() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : new HashMap<>(sharingUntilTimestamp).entrySet()) {
            String peerId = entry.getKey();
            long until = entry.getValue();
            if (until != Long.MAX_VALUE && now > until) {
                liveShareStatus.put(peerId, false);
            }
        }
    }
}
