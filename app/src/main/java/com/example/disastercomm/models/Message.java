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
        HEARTBEAT
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
    public long sharingUntil = 0; // Timestamp when sharing ends (0 = not sharing, Long.MAX_VALUE = continuous)

    // Security Fields
    public String encryptedAesKey; // AES key encrypted with Receiver's Public RSA Key
    public String token; // Routing token (or simply messageID signature)
    public long tokenExpiry; // Timestamp when this packet expires
    public String publicKey; // For KEY_EXCHANGE messages

    public Message() {
    }

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
        this.status = Status.SENDING;
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
