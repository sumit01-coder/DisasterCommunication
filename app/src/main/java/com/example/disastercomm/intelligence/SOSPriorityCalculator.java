package com.example.disastercomm.intelligence;

public class SOSPriorityCalculator {
    public static final String CAT_MEDICAL = "Medical";
    public static final String CAT_FIRE = "Fire";
    public static final String CAT_FLOOD = "Flood";
    public static final String CAT_TRAPPED = "Trapped";
    public static final String CAT_MISSING = "Missing";
    public static final String CAT_FOOD = "Food";
    public static final String CAT_OTHER = "Other";

    /**
     * Calculates SOS Priority Score (1-100)
     * - Medical: +30
     * - Multiple People: +20 (>1 person)
     * - Low Battery: +15 (<15%)
     * - Isolated: +20 (Not implemented here directly, assumed handled elsewhere or by default)
     * - Waiting Time: +15 (Can be added dynamically over time)
     */
    public static int calculateScore(String category, int peopleCount, int batteryLevel, boolean isIsolated, long waitingTimeMs) {
        int score = 0;

        // Base category score
        if (CAT_MEDICAL.equalsIgnoreCase(category)) score += 30;
        else if (CAT_FIRE.equalsIgnoreCase(category)) score += 25;
        else if (CAT_TRAPPED.equalsIgnoreCase(category)) score += 30;
        else if (CAT_FLOOD.equalsIgnoreCase(category)) score += 20;
        else if (CAT_MISSING.equalsIgnoreCase(category)) score += 20;
        else if (CAT_FOOD.equalsIgnoreCase(category)) score += 10;
        else score += 10;

        // People count bonus
        if (peopleCount > 1) {
            score += 20;
        }

        // Low battery bonus
        if (batteryLevel >= 0 && batteryLevel <= 15) {
            score += 15;
        }

        // Isolation bonus
        if (isIsolated) {
            score += 20;
        }

        // Waiting time bonus (max +15 after 30 mins)
        long thirtyMinsMs = 30 * 60 * 1000L;
        if (waitingTimeMs > 0) {
            double timeRatio = Math.min((double) waitingTimeMs / thirtyMinsMs, 1.0);
            score += (int)(timeRatio * 15);
        }

        // Cap at 100
        return Math.min(score, 100);
    }
}
