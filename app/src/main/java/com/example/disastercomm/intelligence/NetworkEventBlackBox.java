package com.example.disastercomm.intelligence;

import android.content.Context;
import android.util.Log;
import com.example.disastercomm.models.Message;

import java.util.ArrayList;
import java.util.List;

public class NetworkEventBlackBox {
    private static final String TAG = "BlackBox";
    private static final int MAX_EVENTS = 500;

    private static final List<Event> eventLog = new ArrayList<>();

    public static class Event {
        public String type;
        public String description;
        public long timestamp;

        public Event(String type, String description) {
            this.type = type;
            this.description = description;
            this.timestamp = System.currentTimeMillis();
        }

        public String toDisplayString() {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss");
            return sdf.format(new java.util.Date(timestamp)) + "  [" + type + "]  " + description;
        }
    }

    public static synchronized void logEvent(String type, String description) {
        if (eventLog.size() >= MAX_EVENTS) {
            eventLog.remove(0); // Remove oldest
        }
        Event event = new Event(type, description);
        eventLog.add(event);
        Log.d(TAG, "📼 " + event.toDisplayString());
    }

    public static synchronized List<Event> getEvents() {
        return new ArrayList<>(eventLog);
    }

    public static synchronized void clear() {
        eventLog.clear();
    }

    // Convenience methods
    public static void logJoined(String deviceName) {
        logEvent("JOIN", deviceName + " joined mesh");
    }

    public static void logLeft(String deviceName) {
        logEvent("LEFT", deviceName + " left mesh");
    }

    public static void logSOSCreated(String sender, String category, int score) {
        logEvent("SOS", sender + " → " + category + " (Priority " + score + ")");
    }

    public static void logRouteLost(String dest) {
        logEvent("ROUTE_LOST", "Route to " + dest + " lost");
    }

    public static void logNewRoute(String dest, String via) {
        logEvent("NEW_ROUTE", "New route to " + dest + " via " + via);
    }

    public static void logDeadZone(double lat, double lng) {
        logEvent("DEAD_ZONE", String.format("Dead zone detected at %.4f, %.4f", lat, lng));
    }

    public static void logRelayBattery(String device, int battery) {
        logEvent("LOW_BATT", "Relay " + device + " at " + battery + "% — excluded");
    }
}
