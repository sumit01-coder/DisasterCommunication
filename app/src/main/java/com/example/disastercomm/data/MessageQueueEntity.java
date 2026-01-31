package com.example.disastercomm.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

/**
 * Database entity for store-and-forward message queue.
 * Messages wait here until a route to the destination becomes available.
 */
@Entity(tableName = "message_queue")
public class MessageQueueEntity {

    @PrimaryKey
    @NonNull
    public String messageId; // Unique message ID

    public String destinationId; // Final recipient device ID
    public String nextHopId; // Next relay (null = waiting for route)
    public byte[] payload; // Serialized message content
    public long expiryTime; // Delete after this timestamp
    public int retryCount; // Number of failed delivery attempts
    public String forwardingStrategy; // RELAY, BROADCAST, DIRECT, EPIDEMIC
    public long queuedTime; // When message was queued
    public int hopCount; // Current hop count
    public int maxHops; // Maximum hops allowed
    public boolean delivered; // Has been successfully sent

    public MessageQueueEntity() {
    }

    @androidx.room.Ignore
    public MessageQueueEntity(@NonNull String messageId, String destinationId, byte[] payload) {
        this.messageId = messageId;
        this.destinationId = destinationId;
        this.payload = payload;
        this.queuedTime = System.currentTimeMillis();
        this.expiryTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000); // 24 hours
        this.retryCount = 0;
        this.hopCount = 0;
        this.maxHops = 10;
        this.delivered = false;
        this.forwardingStrategy = "RELAY";
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }

    public long getAgeMs() {
        return System.currentTimeMillis() - queuedTime;
    }
}
