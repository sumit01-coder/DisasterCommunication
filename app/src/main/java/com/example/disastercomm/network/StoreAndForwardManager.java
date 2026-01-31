package com.example.disastercomm.network;

import android.content.Context;
import android.util.Log;

import com.example.disastercomm.data.AppDatabase;
import com.example.disastercomm.data.MessageQueueDao;
import com.example.disastercomm.data.MessageQueueEntity;
import com.example.disastercomm.models.Message;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Manages store-and-forward messaging for offline delivery.
 * Queues messages when no route available and forwards when path found.
 */
public class StoreAndForwardManager {
    private static final String TAG = "StoreAndForward";
    private static final long CLEANUP_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

    private final Context context;
    private final MessageQueueDao queueDao;
    private final Gson gson = new Gson();
    private final ForwardingCallback callback;
    private long lastCleanupTime = 0;

    public interface ForwardingCallback {
        void forwardMessage(Message message, String nextHop);

        boolean hasRoute(String destinationId);

        String getNextHop(String destinationId);
    }

    public StoreAndForwardManager(Context context, ForwardingCallback callback) {
        this.context = context;
        this.callback = callback;
        AppDatabase db = AppDatabase.getDatabase(context);
        this.queueDao = db.messageQueueDao();
    }

    /**
     * Queue a message for delivery when route becomes available
     */
    public void queueMessage(Message message) {
        Log.d(TAG, "📥 Queuing message " + message.id + " for " + message.receiverId.substring(0, 8));

        String jsonPayload = gson.toJson(message);
        byte[] payload = jsonPayload.getBytes(StandardCharsets.UTF_8);

        MessageQueueEntity queuedMsg = new MessageQueueEntity(
                message.id,
                message.receiverId,
                payload);
        queuedMsg.hopCount = message.hopCount;
        queuedMsg.maxHops = message.maxHops;

        // Check if we have a route now
        if (callback.hasRoute(message.receiverId)) {
            queuedMsg.nextHopId = callback.getNextHop(message.receiverId);
            queuedMsg.forwardingStrategy = "DIRECT";
        } else {
            queuedMsg.nextHopId = null; // No route yet
            queuedMsg.forwardingStrategy = "RELAY";
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            queueDao.insertMessage(queuedMsg);
            Log.d(TAG, "✅ Message queued successfully");
        });
    }

    /**
     * Process queue when a new peer connects or route is discovered
     */
    public void processQueue(String newPeerId) {
        Log.d(TAG, "🔄 Processing queue for new peer: " + (newPeerId != null ? newPeerId.substring(0, 8) : "ALL"));

        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            List<MessageQueueEntity> pending = queueDao.getPendingMessages(now);

            int forwarded = 0;
            for (MessageQueueEntity queued : pending) {
                // Skip if too many retries
                if (queued.retryCount >= 5) {
                    Log.w(TAG, "⚠️ Message " + queued.messageId + " exceeded retry limit, dropping");
                    queueDao.deleteMessage(queued.messageId);
                    continue;
                }

                // Check if we now have route to destination
                if (callback.hasRoute(queued.destinationId)) {
                    String nextHop = callback.getNextHop(queued.destinationId);

                    // Deserialize and forward
                    try {
                        String json = new String(queued.payload, StandardCharsets.UTF_8);
                        Message message = gson.fromJson(json, Message.class);
                        message.hopCount++; // Increment hop

                        callback.forwardMessage(message, nextHop);
                        queueDao.markAsDelivered(queued.messageId);
                        forwarded++;

                        Log.d(TAG, "📤 Forwarded queued message " + queued.messageId +
                                " to " + nextHop.substring(0, 8));
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Failed to forward message: " + e.getMessage());
                        queueDao.incrementRetryCount(queued.messageId);
                    }
                } else if (newPeerId != null && queued.destinationId.equals(newPeerId)) {
                    // Direct connection to destination!
                    try {
                        String json = new String(queued.payload, StandardCharsets.UTF_8);
                        Message message = gson.fromJson(json, Message.class);

                        callback.forwardMessage(message, newPeerId);
                        queueDao.markAsDelivered(queued.messageId);
                        forwarded++;

                        Log.d(TAG, "🎯 Direct delivery of queued message to " + newPeerId.substring(0, 8));
                    } catch (Exception e) {
                        Log.e(TAG, "❌ Failed to send message: " + e.getMessage());
                    }
                }
            }

            if (forwarded > 0) {
                Log.d(TAG, "✅ Forwarded " + forwarded + " queued messages");
            }
        });
    }

    /**
     * Cleanup expired messages periodically
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) {
            return; // Too soon
        }

        lastCleanupTime = now;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int deleted = queueDao.deleteExpired(now);
            if (deleted > 0) {
                Log.d(TAG, "♻️ Cleaned up " + deleted + " expired messages");
            }
        });
    }

    /**
     * Get queue statistics
     */
    public void getQueueStats(QueueStatsCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            int pending = queueDao.getPendingCount(now);
            callback.onStats(pending);
        });
    }

    public interface QueueStatsCallback {
        void onStats(int pendingCount);
    }

    /**
     * Clear all queued messages (for testing/reset)
     */
    public void clearQueue() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            queueDao.deleteAll();
            Log.d(TAG, "🔄 Message queue cleared");
        });
    }
}
