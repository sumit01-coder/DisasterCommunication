package com.example.disastercomm.intelligence;

import java.util.ArrayList;
import java.util.List;

public class DeadZoneAnalyzer {

    public static class DeadZone {
        public double latitude;
        public double longitude;
        public long detectedAt;
        public String description;

        public DeadZone(double lat, double lng, String desc) {
            this.latitude = lat;
            this.longitude = lng;
            this.description = desc;
            this.detectedAt = System.currentTimeMillis();
        }
    }

    private static final List<DeadZone> knownDeadZones = new ArrayList<>();

    /**
     * Reports a dead zone (e.g., when a device suddenly disappears while still having high battery).
     */
    public static void reportDeadZone(double lat, double lng, String description) {
        // Prevent duplicate spam
        for (DeadZone dz : knownDeadZones) {
            if (Math.abs(dz.latitude - lat) < 0.001 && Math.abs(dz.longitude - lng) < 0.001) {
                return; // Already known
            }
        }
        knownDeadZones.add(new DeadZone(lat, lng, description));
    }

    public static List<DeadZone> getActiveDeadZones() {
        return new ArrayList<>(knownDeadZones);
    }
    
    public static void clearDeadZones() {
        knownDeadZones.clear();
    }
}
