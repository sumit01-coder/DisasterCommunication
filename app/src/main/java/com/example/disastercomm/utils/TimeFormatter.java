package com.example.disastercomm.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for formatting message timestamps in a human-readable way
 */
public class TimeFormatter {

    /**
     * Format timestamp to human-readable string
     * - "Just now" for < 1 minute
     * - "X min ago" for < 60 minutes
     * - "Today at HH:MM" for today
     * - "Yesterday at HH:MM" for yesterday
     * - "DD MMM at HH:MM" for older messages
     */
    public static String formatMessageTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        // Just now (< 1 minute)
        if (diff < TimeUnit.MINUTES.toMillis(1)) {
            return "Just now";
        }

        // X min ago (< 60 minutes)
        if (diff < TimeUnit.HOURS.toMillis(1)) {
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            return minutes + " min ago";
        }

        // Check if today
        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String messageDay = dayFormat.format(new Date(timestamp));
        String todayDay = dayFormat.format(new Date(now));

        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String time = timeFormat.format(new Date(timestamp));

        if (messageDay.equals(todayDay)) {
            return "Today at " + time;
        }

        // Check if yesterday
        String yesterdayDay = dayFormat.format(new Date(now - TimeUnit.DAYS.toMillis(1)));
        if (messageDay.equals(yesterdayDay)) {
            return "Yesterday at " + time;
        }

        // Older messages
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM", Locale.getDefault());
        String date = dateFormat.format(new Date(timestamp));
        return date + " at " + time;
    }

    /**
     * Get short time format for compact display (e.g., in member list)
     * - "Now" for < 1 minute
     * - "Xm" for < 60 minutes
     * - "Xh" for < 24 hours
     * - "Xd" for < 7 days
     * - "DD/MM" for older
     */
    public static String formatShortTime(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        if (diff < TimeUnit.MINUTES.toMillis(1)) {
            return "Now";
        }

        if (diff < TimeUnit.HOURS.toMillis(1)) {
            return TimeUnit.MILLISECONDS.toMinutes(diff) + "m";
        }

        if (diff < TimeUnit.DAYS.toMillis(1)) {
            return TimeUnit.MILLISECONDS.toHours(diff) + "h";
        }

        if (diff < TimeUnit.DAYS.toMillis(7)) {
            return TimeUnit.MILLISECONDS.toDays(diff) + "d";
        }

        SimpleDateFormat format = new SimpleDateFormat("dd/MM", Locale.getDefault());
        return format.format(new Date(timestamp));
    }

    /**
     * Get status icon based on message status
     * 
     * @param status Message.Status enum
     * @return String icon (✓, ✓✓, or ✓✓ with color)
     */
    public static String getStatusIcon(com.example.disastercomm.models.Message.Status status) {
        if (status == null)
            return "";

        switch (status) {
            case SENDING:
                return "⏱"; // Clock icon
            case SENT:
                return "✓"; // Single check
            case DELIVERED:
                return "✓✓"; // Double check
            case READ:
                return "✓✓"; // Double check (will be colored blue)
            case FAILED:
                return "✗"; // X mark
            default:
                return "";
        }
    }

    /**
     * Get color for status icon
     */
    public static int getStatusColor(com.example.disastercomm.models.Message.Status status,
            android.content.Context context) {
        if (status == null)
            return android.graphics.Color.GRAY;

        switch (status) {
            case SENDING:
                return android.graphics.Color.GRAY;
            case SENT:
                return android.graphics.Color.GRAY;
            case DELIVERED:
                return android.graphics.Color.GRAY;
            case READ:
                return context.getResources().getColor(com.example.disastercomm.R.color.primary, null);
            case FAILED:
                return context.getResources().getColor(com.example.disastercomm.R.color.error, null);
            default:
                return android.graphics.Color.GRAY;
        }
    }
}
