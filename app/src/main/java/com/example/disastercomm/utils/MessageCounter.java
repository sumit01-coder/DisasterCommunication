package com.example.disastercomm.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

/**
 * ✅ Utility class to track unread message counts per conversation
 */
public class MessageCounter {
    private static final String PREFS_NAME = "MessageCounterPrefs";
    private static final String KEY_PREFIX = "unread_";

    private static MessageCounter instance;
    private final SharedPreferences prefs;
    private final Map<String, Integer> counters = new HashMap<>();
    private CountChangeListener listener;

    public interface CountChangeListener {
        void onCountChanged(String memberId, int newCount);
    }

    private MessageCounter(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadCounts();
    }

    public static synchronized MessageCounter getInstance(Context context) {
        if (instance == null) {
            instance = new MessageCounter(context);
        }
        return instance;
    }

    /**
     * Load all saved counts from SharedPreferences
     */
    private void loadCounts() {
        Map<String, ?> all = prefs.getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            if (entry.getKey().startsWith(KEY_PREFIX)) {
                String memberId = entry.getKey().substring(KEY_PREFIX.length());
                counters.put(memberId, (Integer) entry.getValue());
            }
        }
    }

    /**
     * Increment unread count for a member
     */
    public void increment(String memberId) {
        int current = counters.getOrDefault(memberId, 0);
        int newCount = current + 1;
        counters.put(memberId, newCount);
        prefs.edit().putInt(KEY_PREFIX + memberId, newCount).apply();

        if (listener != null) {
            listener.onCountChanged(memberId, newCount);
        }
    }

    /**
     * Reset count for a member (when chat is opened)
     */
    public void reset(String memberId) {
        counters.remove(memberId);
        prefs.edit().remove(KEY_PREFIX + memberId).apply();

        if (listener != null) {
            listener.onCountChanged(memberId, 0);
        }
    }

    /**
     * Get unread count for a member
     */
    public int getCount(String memberId) {
        return counters.getOrDefault(memberId, 0);
    }

    /**
     * Get total unread count across all conversations
     */
    public int getTotalCount() {
        int total = 0;
        for (int count : counters.values()) {
            total += count;
        }
        return total;
    }

    /**
     * Set listener for count changes
     */
    public void setCountChangeListener(CountChangeListener listener) {
        this.listener = listener;
    }

    /**
     * Clear all counts
     */
    public void clearAll() {
        counters.clear();
        prefs.edit().clear().apply();
    }
}
