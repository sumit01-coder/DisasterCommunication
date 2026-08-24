package com.example.disastercomm.intelligence;

import com.example.disastercomm.intelligence.DeadZoneAnalyzer.DeadZone;
import java.util.List;

public class LoRaDeploymentRecommender {

    public static class Recommendation {
        public double latitude;
        public double longitude;
        public String reason;
        public double coverageGainPercent;

        public Recommendation(double lat, double lng, String reason, double gain) {
            this.latitude = lat;
            this.longitude = lng;
            this.reason = reason;
            this.coverageGainPercent = gain;
        }

        public String toDisplayString() {
            return String.format("Deploy relay at (%.5f, %.5f)\nReason: %s\nEst. Coverage Gain: +%.0f%%",
                    latitude, longitude, reason, coverageGainPercent);
        }
    }

    /**
     * For each identified dead zone, recommend deploying a LoRa/ESP32 relay
     * at or near the dead zone's center.
     */
    public static List<Recommendation> generateRecommendations() {
        List<DeadZone> deadZones = DeadZoneAnalyzer.getActiveDeadZones();
        List<Recommendation> recommendations = new java.util.ArrayList<>();

        for (DeadZone dz : deadZones) {
            recommendations.add(new Recommendation(
                    dz.latitude,
                    dz.longitude,
                    "Dead zone detected — no device coverage for >2 minutes",
                    25.0 // Estimated ~25% coverage gain per LoRa relay
            ));
        }

        return recommendations;
    }
}
