package com.example.disastercomm.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * DAO for message queue operations (store-and-forward)
 */
@Dao
public interface MessageQueueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(MessageQueueEntity message);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<MessageQueueEntity> messages);

    @Query("SELECT * FROM message_queue WHERE delivered = 0 AND expiryTime > :currentTime ORDER BY queuedTime ASC")
    List<MessageQueueEntity> getPendingMessages(long currentTime);

    @Query("SELECT * FROM message_queue WHERE destinationId = :destId AND delivered = 0 AND expiryTime > :currentTime")
    List<MessageQueueEntity> getMessagesForDestination(String destId, long currentTime);

    @Query("SELECT * FROM message_queue WHERE nextHopId = :nextHop AND delivered = 0 AND expiryTime > :currentTime")
    List<MessageQueueEntity> getMessagesForNextHop(String nextHop, long currentTime);

    @Query("UPDATE message_queue SET delivered = 1 WHERE messageId = :messageId")
    void markAsDelivered(String messageId);

    @Query("UPDATE message_queue SET retryCount = retryCount + 1 WHERE messageId = :messageId")
    void incrementRetryCount(String messageId);

    @Query("UPDATE message_queue SET nextHopId = :nextHop WHERE messageId = :messageId")
    void updateNextHop(String messageId, String nextHop);

    @Query("DELETE FROM message_queue WHERE expiryTime < :currentTime OR delivered = 1")
    int deleteExpired(long currentTime);

    @Query("DELETE FROM message_queue WHERE messageId = :messageId")
    void deleteMessage(String messageId);

    @Query("SELECT COUNT(*) FROM message_queue WHERE delivered = 0 AND expiryTime > :currentTime")
    int getPendingCount(long currentTime);

    @Query("DELETE FROM message_queue")
    void deleteAll();
}
