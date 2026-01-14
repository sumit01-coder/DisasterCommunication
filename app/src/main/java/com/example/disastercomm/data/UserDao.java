package com.example.disastercomm.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.disastercomm.models.User;

import java.util.List;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(User user);

    @Query("SELECT * FROM users ORDER BY lastMessageTimestamp DESC")
    LiveData<List<User>> getAllUsers();

    @Query("SELECT * FROM users WHERE id = :userId")
    User getUser(String userId);

    @Query("UPDATE users SET lastMessagePreview = :preview, lastMessageTimestamp = :timestamp WHERE id = :userId")
    void updateLastMessage(String userId, String preview, long timestamp);

    @Query("UPDATE users SET isOnline = :status WHERE id = :userId")
    void updateUserStatus(String userId, boolean status);
}
