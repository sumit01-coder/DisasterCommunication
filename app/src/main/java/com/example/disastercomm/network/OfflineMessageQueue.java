package com.example.disastercomm.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.disastercomm.models.Message;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Offline Message Queue - Persists unsent messages and retries when connection
 * available
 */
public class OfflineMessageQueue {
    private static final String TAG = "OfflineQueue";
    private static final String PREFS_NAME = "offline_message_queue";
    private static final String KEY_QUEUE = "pending_messages";

    private final Queue<Message> pendingMessages = new ConcurrentLinkedQueue<>();
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public OfflineMessageQueue(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFromStorage();
    }

    /**
     * Add message to offline queue
     */
    public void enqueue(Message message) {
        pendingMessages.add(message);
        persistToStorage();
        Log.d(TAG, "Message queued: " + message.id + " (queue size: " + pendingMessages.size() + ")");
    }

    /**
     * Get all pending messages
     */
    public List<Message> getPendingMessages() {
        return new ArrayList<>(pendingMessages);
    }

    /**
     * Remove message from queue (after successful send)
     */
    public void remove(Message message) {
        pendingMessages.remove(message);
        persistToStorage();
        Log.d(TAG, "Message removed from queue: " + message.id);
    }

    /**
     * Clear all pending messages
     */
    public void clear() {
        pendingMessages.clear();
        persistToStorage();
        Log.d(TAG, "Queue cleared");
    }

    /**
     * Get queue size
     */
    public int size() {
        return pendingMessages.size();
    }

    /**
     * Check if queue is empty
     */
    public boolean isEmpty() {
        return pendingMessages.isEmpty();
    }

    /**
     * Persist queue to SharedPreferences
     */
    private void persistToStorage() {
        List<Message> messageList = new ArrayList<>(pendingMessages);
        String json = gson.toJson(messageList);
        prefs.edit().putString(KEY_QUEUE, json).apply();
    }

    /**
     * Load queue from SharedPreferences
     */
    private void loadFromStorage() {
        String json = prefs.getString(KEY_QUEUE, null);
        if (json != null) {
            try {
                Type listType = new TypeToken<ArrayList<Message>>() {
                }.getType();
                List<Message> messages = gson.fromJson(json, listType);
                if (messages != null) {
                    pendingMessages.addAll(messages);
                    Log.d(TAG, "Loaded " + messages.size() + " messages from storage");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load queue from storage", e);
            }
        }
    }
}
