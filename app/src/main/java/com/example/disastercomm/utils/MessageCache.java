package com.example.disastercomm.utils;

import com.example.disastercomm.models.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LRU Message Cache for instant chat display
 * Caches recent messages in memory for fast access
 */
public class MessageCache {
    private static final int MAX_CACHE_SIZE = 500; // Cache up to 500 messages
    private static final int MESSAGES_PER_CHAT = 100; // 100 messages per chat

    private static MessageCache instance;

    // LRU cache using LinkedHashMap
    private final Map<String, List<Message>> cache = new LinkedHashMap<String, List<Message>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<Message>> eldest) {
            // Remove oldest chat when cache exceeds size
            return size() > (MAX_CACHE_SIZE / MESSAGES_PER_CHAT);
        }
    };

    private MessageCache() {
    }

    public static synchronized MessageCache getInstance() {
        if (instance == null) {
            instance = new MessageCache();
        }
        return instance;
    }

    /**
     * Get chat key (for global or private chats)
     */
    private String getChatKey(String myId, String otherId) {
        if (otherId == null || otherId.equals("ALL")) {
            return "GLOBAL";
        }
        // Ensure consistent key regardless of order
        return myId.compareTo(otherId) < 0
                ? myId + "_" + otherId
                : otherId + "_" + myId;
    }

    /**
     * Add message to cache
     */
    public synchronized void addMessage(String myId, String otherId, Message message) {
        String key = getChatKey(myId, otherId);
        List<Message> messages = cache.get(key);

        if (messages == null) {
            messages = new ArrayList<>();
            cache.put(key, messages);
        }

        // Add message if not duplicate
        if (!messages.contains(message)) {
            messages.add(message);

            // Keep only last 100 messages per chat
            if (messages.size() > MESSAGES_PER_CHAT) {
                messages.remove(0);
            }
        }
    }

    /**
     * Get cached messages for a chat
     */
    public synchronized List<Message> getMessages(String myId, String otherId) {
        String key = getChatKey(myId, otherId);
        List<Message> messages = cache.get(key);
        return messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    /**
     * Check if chat has cached messages
     */
    public synchronized boolean hasMessages(String myId, String otherId) {
        String key = getChatKey(myId, otherId);
        List<Message> messages = cache.get(key);
        return messages != null && !messages.isEmpty();
    }

    /**
     * Clear cache for specific chat
     */
    public synchronized void clearChat(String myId, String otherId) {
        String key = getChatKey(myId, otherId);
        cache.remove(key);
    }

    /**
     * Clear all cache
     */
    public synchronized void clearAll() {
        cache.clear();
    }

    /**
     * Get cache size
     */
    public synchronized int size() {
        return cache.size();
    }
}
