package com.example.disastercomm.models;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;
import androidx.annotation.NonNull;

import java.util.UUID;

@Entity(tableName = "messages")
public class Message {

    public enum Type {
        TEXT,
        SOS,
        GOVT_ALERT,
        LOCATION_UPDATE,
        DELIVERY_RECEIPT,
        READ_RECEIPT,
        KEY_EXCHANGE,
        HEARTBEAT,
        ROUTE_REQUEST, 
        ROUTE_REPLY, 
        ROUTE_ERROR,
        MAP_MARKER,
        NETWORK_STATE_REQUEST,
        IMAGE
    }

    public enum Status {
        SENDING,
        SENT,
        DELIVERED,
        READ,
        FAILED
    }

    @PrimaryKey
    @NonNull
    public String id;
    public String senderId;
    public String senderName;
    public String receiverId;
    @TypeConverters(MessageTypeConverter.class)
    public Type type;
    public String content;
    public long timestamp;
    public int ttl;

    @TypeConverters(MessageStatusConverter.class)
    public Status status = Status.SENDING;
    public String receiptFor;
    public boolean isRead = false;
    public long deliveredTime = 0;
    public long readTime = 0;

    // Live Location Sharing Fields
    public boolean isLiveSharing = false;
    public long sharingUntil = 0; 

    // Sync Fields
    public boolean isHistorySync = false;

    // Security Fields
    public String encryptedAesKey; 
    public String token; 
    public long tokenExpiry; 
    public String publicKey; 

    // ===== MESH ROUTING FIELDS =====
    public int hopCount = 0; 
    public int maxHops = 15; // Default, can be overridden by Adaptive TTL
    public String routePath = ""; 
    public String nextHop = null; 
    public String originatorId = null; 
    public int routeSequence = 0; 

    // CIA Security & Availability Fields
    public String signature; // Integrity: Digital Signature
    public int priority = 1; // Availability: 1-10 (Legacy)
    public int priorityScore = 0; // 1-100 (Smart SOS)
    public String emergencyCategory = ""; // Medical, Fire, Flood, etc.
    public int peopleCount = 1;
    public int batteryLevel = -1;
    public double latitude = 0.0;
    public double longitude = 0.0;
    public String nonce; // Integrity: Anti-Replay
    public boolean isEncrypted = false; // Confidentiality indicator

    public Message() {}

    @androidx.room.Ignore
    public Message(String senderId, String senderName, Type type, String content) {
        this.id = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.senderName = senderName;
        this.receiverId = "ALL";
        this.type = type;
        this.content = content;
        this.timestamp = System.currentTimeMillis();
        this.ttl = 10;
        this.maxHops = 15;
        this.hopCount = 0;
        this.routePath = senderId;
        this.status = Status.SENDING;
        this.nonce = UUID.randomUUID().toString();
        if (type == Type.SOS) {
            this.priority = 10;
            this.priorityScore = 50; // Default base score
        }
    }

    public static Message createDeliveryReceipt(String messageId, String senderId, String senderName) {
        Message receipt = new Message(senderId, senderName, Type.DELIVERY_RECEIPT, "Delivered");
        receipt.receiptFor = messageId;
        receipt.ttl = 5;
        return receipt;
    }

    public static Message createReadReceipt(String messageId, String senderId, String senderName) {
        Message receipt = new Message(senderId, senderName, Type.READ_RECEIPT, "Read");
        receipt.receiptFor = messageId;
        receipt.ttl = 5;
        return receipt;
    }

    /**
     * Adaptive TTL: Calculate maxHops based on network density.
     * Sparse network = higher hops to reach distant islands.
     * Dense network = lower hops to prevent broadcast storms.
     */
    public static int calculateAdaptiveMaxHops(int connectedPeersCount) {
        return connectedPeersCount < 3 ? 25 : 15;
    }

    public static class MessageTypeConverter {
        @TypeConverter
        public static Type toType(String type) {
            return type == null ? null : Type.valueOf(type);
        }

        @TypeConverter
        public static String fromType(Type type) {
            return type == null ? null : type.name();
        }
    }

    public static class MessageStatusConverter {
        @TypeConverter
        public static Status toStatus(String status) {
            return status == null ? null : Status.valueOf(status);
        }

        @TypeConverter
        public static String fromStatus(Status status) {
            return status == null ? null : status.name();
        }
    }
}
