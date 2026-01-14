package com.example.disastercomm.models;

public class MemberItem {
    public String id;
    public String name;
    public int distance; // in meters
    public String lastActive; // e.g., "Active now", "2 min ago"
    public String signalStrength; // "strong", "medium", "weak"
    public String connectionSource; // "WiFi Direct", "Bluetooth", "Mesh"
    public boolean isOnline;
    public boolean isSecure; // Secure connection available (RSA Key exchanged)
    public double latitude;
    public double longitude;

    // ✅ NEW FIELDS for enhanced display
    public String lastMessagePreview; // Preview of last message
    public int unreadCount; // Number of unread messages
    public int connectionQuality; // Connection quality percentage (0-100)
    public int hopCount; // Number of hops in mesh network
    public long lastSeenTimestamp; // Timestamp when last seen online
    public boolean isTyping; // Whether user is currently typing

    // Live Location Sharing Fields
    public boolean isLiveSharing = false; // Whether actively sharing live location
    public long lastLocationUpdate = 0; // Timestamp of last location update
    public long sharingUntil = 0; // Timestamp when live sharing ends

    public MemberItem(String id, String name) {
        this.id = id;
        this.name = name;
        this.isOnline = true;
        this.lastActive = "Active now";
        this.signalStrength = "strong";
        this.connectionSource = "Mesh"; // Default
        this.distance = 0;
        this.unreadCount = 0;
        this.connectionQuality = 100;
        this.hopCount = 1;
        this.lastSeenTimestamp = System.currentTimeMillis();
        this.isTyping = false;
    }

    public String getDistanceText() {
        if (distance < 1000) {
            return distance + "m away";
        } else {
            return String.format("%.1fkm away", distance / 1000.0);
        }
    }

    public String getSignalBadgeColor() {
        switch (signalStrength.toLowerCase()) {
            case "strong":
                return "#10B981"; // Green
            case "medium":
                return "#F59E0B"; // Orange
            case "weak":
                return "#EF4444"; // Red
            default:
                return "#9E9E9E"; // Gray
        }
    }

    /**
     * ✅ Get human-readable "last seen" text
     */
    public String getLastSeenText() {
        if (isOnline) {
            if (isTyping) {
                return "typing...";
            }
            return "Active now";
        }

        long diff = System.currentTimeMillis() - lastSeenTimestamp;
        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);
        long days = diff / (24 * 60 * 60 * 1000);

        if (minutes < 1) {
            return "Just now";
        } else if (minutes < 60) {
            return minutes + " min ago";
        } else if (hours < 24) {
            return hours + " hr ago";
        } else if (days < 7) {
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        } else {
            return "Long time ago";
        }
    }

    /**
     * ✅ Get connection type icon emoji
     */
    public String getConnectionIcon() {
        if (connectionSource == null)
            return "📡";
        switch (connectionSource.toLowerCase()) {
            case "bluetooth":
                return "🔵"; // Bluetooth icon
            case "wifi direct":
            case "mesh":
                return "📡"; // WiFi/Mesh icon
            default:
                return "🌐";
        }
    }

    /**
     * ✅ Get connection quality color
     */
    public String getConnectionQualityColor() {
        if (connectionQuality >= 80) {
            return "#10B981"; // Green
        } else if (connectionQuality >= 50) {
            return "#F59E0B"; // Orange
        } else {
            return "#EF4444"; // Red
        }
    }

    /**
     * ✅ Get message preview with truncation
     */
    public String getMessagePreview() {
        if (lastMessagePreview == null || lastMessagePreview.isEmpty()) {
            return "No messages yet";
        }
        if (lastMessagePreview.length() > 40) {
            return lastMessagePreview.substring(0, 37) + "...";
        }
        return lastMessagePreview;
    }
}
