package com.example.disastercomm.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.disastercomm.models.Message;

import java.util.List;

@Dao
public interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMessage(Message message);

    @androidx.room.Update
    void updateMessage(Message message);

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    List<Message> getAllMessages();

    // Get messages for a specific private chat
    @Query("SELECT * FROM messages WHERE (senderId = :myId AND receiverId = :otherId) OR (senderId = :otherId AND receiverId = :myId) ORDER BY timestamp ASC")
    List<Message> getPrivateChatMessages(String myId, String otherId);

    // Get global chat messages
    @Query("SELECT * FROM messages WHERE receiverId = 'ALL' AND type = 'TEXT' ORDER BY timestamp ASC")
    List<Message> getGlobalChatMessages();

    // Get SOS messages
    @Query("SELECT * FROM messages WHERE type = 'SOS' ORDER BY timestamp ASC")
    List<Message> getSosMessages();

    // ✅ PAGINATION: Get recent private messages (limit)
    @Query("SELECT * FROM messages WHERE (senderId = :myId AND receiverId = :otherId) OR (senderId = :otherId AND receiverId = :myId) ORDER BY timestamp DESC LIMIT :limit")
    List<Message> getRecentPrivateMessages(String myId, String otherId, int limit);

    // ✅ PAGINATION: Get recent global messages (limit)
    @Query("SELECT * FROM messages WHERE receiverId = 'ALL' AND type = 'TEXT' ORDER BY timestamp DESC LIMIT :limit")
    List<Message> getRecentGlobalMessages(int limit);

    // ✅ PAGINATION: Get recent SOS messages (limit)
    @Query("SELECT * FROM messages WHERE type = 'SOS' ORDER BY timestamp DESC LIMIT :limit")
    List<Message> getRecentSosMessages(int limit);
}
