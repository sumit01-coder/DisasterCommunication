package com.example.disastercomm.models;

public class PeerItem {
    public enum Type {
        BLUETOOTH,
        MESH,
        HEADER
    }

    public String id;
    public String displayName;
    public Type type;
    public boolean isNew; // Indicates newly discovered device
    public String address; // Bluetooth MAC address or endpoint ID
    public int signalStrength; // 0-100 for Bluetooth RSSI, or hop count for mesh
    public long discoveryTime; // Timestamp when device was discovered

    public PeerItem(String id, String displayName, Type type) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.isNew = false;
        this.address = id;
        this.signalStrength = -1;
        this.discoveryTime = System.currentTimeMillis();
    }

    public PeerItem(String id, String displayName, Type type, boolean isNew) {
        this(id, displayName, type);
        this.isNew = isNew;
    }
}
