package com.example.disastercomm.models;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class User {
    @PrimaryKey
    @NonNull
    public String id; // Device ID

    public String name;
    public long lastSeenTimestamp;
    public String lastMessagePreview;
    public long lastMessageTimestamp;

    // Status (not persisted, used for UI)
    public boolean isOnline;

    public User(@NonNull String id, String name) {
        this.id = id;
        this.name = name;
        this.lastSeenTimestamp = System.currentTimeMillis();
    }
}
