package com.example.disastercomm.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager to control live location sharing state
 */
public class LiveLocationSharingManager {
    private static LiveLocationSharingManager instance;
    private final SharedPreferences prefs;

    private boolean isSharingActive = false;
    private long sharingStartTime = 0;
    private long sharingDuration = 0; // in milliseconds, 0 = continuous
    private final List<SharingStatusListener> listeners = new ArrayList<>();

    // Duration constants (in milliseconds)
    public static final long DURATION_15_MIN = 15 * 60 * 1000;
    public static final long DURATION_30_MIN = 30 * 60 * 1000;
    public static final long DURATION_1_HOUR = 60 * 60 * 1000;
    public static final long DURATION_CONTINUOUS = 0;

    private static final String PREFS_NAME = "LiveLocationPrefs";
    private static final String KEY_IS_SHARING = "is_sharing";
    private static final String KEY_START_TIME = "start_time";
    private static final String KEY_DURATION = "duration";

    public interface SharingStatusListener {
        void onSharingStarted(long duration);

        void onSharingStopped();

        void onDurationUpdated(long remainingMs);
    }

    private LiveLocationSharingManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadState();
    }

    public static synchronized LiveLocationSharingManager getInstance(Context context) {
        if (instance == null) {
            instance = new LiveLocationSharingManager(context);
        }
        return instance;
    }

    public void addListener(SharingStatusListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(SharingStatusListener listener) {
        listeners.remove(listener);
    }

    /**
     * Start sharing location with specified duration
     * 
     * @param durationMs Duration in milliseconds, 0 for continuous
     */
    public void startSharing(long durationMs) {
        isSharingActive = true;
        sharingStartTime = System.currentTimeMillis();
        sharingDuration = durationMs;
        saveState();

        for (SharingStatusListener listener : listeners) {
            listener.onSharingStarted(durationMs);
        }
    }

    /**
     * Stop sharing location
     */
    public void stopSharing() {
        isSharingActive = false;
        sharingStartTime = 0;
        sharingDuration = 0;
        saveState();

        for (SharingStatusListener listener : listeners) {
            listener.onSharingStopped();
        }
    }

    /**
     * Check if sharing is currently active
     */
    public boolean isSharingActive() {
        // Auto-stop if duration has expired
        if (isSharingActive && sharingDuration > 0) {
            long elapsed = System.currentTimeMillis() - sharingStartTime;
            if (elapsed >= sharingDuration) {
                stopSharing();
                return false;
            }
        }
        return isSharingActive;
    }

    /**
     * Get remaining sharing time in milliseconds
     * 
     * @return remaining time, or -1 if continuous, or 0 if expired/not sharing
     */
    public long getRemainingTime() {
        if (!isSharingActive) {
            return 0;
        }

        if (sharingDuration == 0) {
            return -1; // Continuous
        }

        long elapsed = System.currentTimeMillis() - sharingStartTime;
        long remaining = sharingDuration - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Get the timestamp when sharing will end
     * 
     * @return timestamp in milliseconds, or 0 if continuous/not sharing
     */
    public long getSharingUntilTimestamp() {
        if (!isSharingActive) {
            return 0;
        }

        if (sharingDuration == 0) {
            return Long.MAX_VALUE; // Continuous
        }

        return sharingStartTime + sharingDuration;
    }

    public long getSharingDuration() {
        return sharingDuration;
    }

    public long getSharingStartTime() {
        return sharingStartTime;
    }

    /**
     * Notify listeners about duration update (call periodically)
     */
    public void updateDuration() {
        if (isSharingActive()) {
            long remaining = getRemainingTime();
            for (SharingStatusListener listener : listeners) {
                listener.onDurationUpdated(remaining);
            }
        }
    }

    private void saveState() {
        prefs.edit()
                .putBoolean(KEY_IS_SHARING, isSharingActive)
                .putLong(KEY_START_TIME, sharingStartTime)
                .putLong(KEY_DURATION, sharingDuration)
                .apply();
    }

    private void loadState() {
        isSharingActive = prefs.getBoolean(KEY_IS_SHARING, false);
        sharingStartTime = prefs.getLong(KEY_START_TIME, 0);
        sharingDuration = prefs.getLong(KEY_DURATION, 0);

        // Check if expired
        if (isSharingActive) {
            isSharingActive(); // This will auto-stop if expired
        }
    }

    /**
     * Format duration for display
     */
    public static String formatDuration(long durationMs) {
        if (durationMs == 0 || durationMs == Long.MAX_VALUE) {
            return "Continuous";
        }

        long minutes = durationMs / (60 * 1000);
        if (minutes < 60) {
            return minutes + " min";
        } else {
            long hours = minutes / 60;
            long remainingMins = minutes % 60;
            if (remainingMins == 0) {
                return hours + " hr";
            } else {
                return hours + " hr " + remainingMins + " min";
            }
        }
    }

    /**
     * Format remaining time for display
     */
    public static String formatRemainingTime(long remainingMs) {
        if (remainingMs == -1) {
            return "Continuous";
        }

        if (remainingMs <= 0) {
            return "Expired";
        }

        long seconds = remainingMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            long mins = minutes % 60;
            return String.format("%dh %dm", hours, mins);
        } else if (minutes > 0) {
            long secs = seconds % 60;
            return String.format("%dm %ds", minutes, secs);
        } else {
            return seconds + "s";
        }
    }
}
