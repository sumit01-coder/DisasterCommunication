package com.example.disastercomm.network;

import android.util.Log;

import com.example.disastercomm.models.Message;

/**
 * Local message logger - Firebase removed for pure Java implementation.
 * This class now only logs to Android Logcat for debugging purposes.
 */
public class FirebaseLogger {
    private static final String TAG = "MessageLogger";

    public FirebaseLogger() {
        // No-op constructor - no Firebase initialization needed
    }

    public void logMessage(Message message) {
        if (message == null)
            return;

        // Only log to Android Logcat (local debugging)
        Log.d(TAG, "Message logged locally: " +
                "id=" + message.id +
                ", sender=" + message.senderName +
                ", type=" + message.type +
                ", timestamp=" + message.timestamp);
    }
}
