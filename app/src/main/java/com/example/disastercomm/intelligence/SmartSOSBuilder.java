package com.example.disastercomm.intelligence;

import com.example.disastercomm.models.Message;

public class SmartSOSBuilder {
    private String senderId;
    private String senderName;
    private String content;
    private String category;
    private int peopleCount = 1;
    private int batteryLevel = -1;
    private double latitude = 0.0;
    private double longitude = 0.0;
    private boolean isIsolated = false;
    private long waitingTimeMs = 0;

    public SmartSOSBuilder(String senderId, String senderName) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.category = SOSPriorityCalculator.CAT_OTHER;
    }

    public SmartSOSBuilder setContent(String content) {
        this.content = content;
        return this;
    }

    public SmartSOSBuilder setCategory(String category) {
        this.category = category;
        return this;
    }

    public SmartSOSBuilder setPeopleCount(int peopleCount) {
        this.peopleCount = peopleCount;
        return this;
    }

    public SmartSOSBuilder setBatteryLevel(int batteryLevel) {
        this.batteryLevel = batteryLevel;
        return this;
    }

    public SmartSOSBuilder setLocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        return this;
    }

    public SmartSOSBuilder setIsIsolated(boolean isIsolated) {
        this.isIsolated = isIsolated;
        return this;
    }

    public SmartSOSBuilder setWaitingTimeMs(long waitingTimeMs) {
        this.waitingTimeMs = waitingTimeMs;
        return this;
    }

    public Message build() {
        Message sos = new Message(senderId, senderName, Message.Type.SOS, content);
        sos.emergencyCategory = this.category;
        sos.peopleCount = this.peopleCount;
        sos.batteryLevel = this.batteryLevel;
        sos.latitude = this.latitude;
        sos.longitude = this.longitude;

        sos.priorityScore = SOSPriorityCalculator.calculateScore(
                this.category,
                this.peopleCount,
                this.batteryLevel,
                this.isIsolated,
                this.waitingTimeMs
        );

        // Map high priority score back to legacy priority field
        if (sos.priorityScore >= 90) sos.priority = 10;
        else if (sos.priorityScore >= 70) sos.priority = 9;
        else if (sos.priorityScore >= 50) sos.priority = 8;
        else sos.priority = 7;

        return sos;
    }
}
