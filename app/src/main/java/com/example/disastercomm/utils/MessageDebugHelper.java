package com.example.disastercomm.utils;

import android.util.Log;

/**
 * ✅ Debug helper for messaging system
 * Provides comprehensive logging for message flow tracking
 */
public class MessageDebugHelper {
    private static final String TAG = "MessageDebug";
    private static boolean debugEnabled = true;

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    // ✅ Message Sending
    public static void logMessageSent(String messageId, String recipientId, String content) {
        if (!debugEnabled)
            return;
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "📤 MESSAGE SENT");
        Log.d(TAG, "   ID: " + messageId);
        Log.d(TAG, "   To: " + (recipientId != null ? recipientId : "ALL (Broadcast)"));
        Log.d(TAG, "   Content: " + truncate(content, 50));
        Log.d(TAG, "═══════════════════════════════════════");
    }

    // ✅ Message Receiving
    public static void logMessageReceived(String messageId, String senderId, String content, String receiverId) {
        if (!debugEnabled)
            return;
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "📥 MESSAGE RECEIVED");
        Log.d(TAG, "   ID: " + messageId);
        Log.d(TAG, "   From: " + senderId);
        Log.d(TAG, "   To: " + (receiverId != null ? receiverId : "Unknown"));
        Log.d(TAG, "   Content: " + truncate(content, 50));
        Log.d(TAG, "═══════════════════════════════════════");
    }

    // ✅ Message Routing
    public static void logMessageRouting(String messageId, String currentChatId, String messagePartnerId,
            boolean willDisplay) {
        if (!debugEnabled)
            return;
        Log.d(TAG, "🔀 MESSAGE ROUTING");
        Log.d(TAG, "   Message ID: " + messageId);
        Log.d(TAG, "   Current Chat: " + (currentChatId != null ? currentChatId : "Global"));
        Log.d(TAG, "   Message Partner: " + (messagePartnerId != null ? messagePartnerId : "N/A"));
        Log.d(TAG, "   Will Display: " + (willDisplay ? "✅ YES" : "❌ NO (notification instead)"));
    }

    // ✅ Unread Count
    public static void logUnreadCountUpdate(String memberId, int newCount, String reason) {
        if (!debugEnabled)
            return;
        Log.d(TAG, "🔢 UNREAD COUNT UPDATE");
        Log.d(TAG, "   Member: " + memberId);
        Log.d(TAG, "   New Count: " + newCount);
        Log.d(TAG, "   Reason: " + reason);
    }

    // ✅ Message Preview
    public static void logMessagePreviewUpdate(String memberId, String preview) {
        if (!debugEnabled)
            return;
        Log.d(TAG, "💬 MESSAGE PREVIEW UPDATE");
        Log.d(TAG, "   Member: " + memberId);
        Log.d(TAG, "   Preview: " + truncate(preview, 40));
    }

    // ✅ Database Operations
    public static void logDatabaseSave(String messageId, String table) {
        if (!debugEnabled)
            return;
        Log.d(TAG, "💾 DATABASE SAVE");
        Log.d(TAG, "   Message ID: " + messageId);
        Log.d(TAG, "   Table: " + table);
    }

    // ✅ Transport Layer
    public static void logTransportSend(String endpointId, String transport, int bytes) {
        if (!debugEnabled)
            return;
        Log.d(TAG, "📡 TRANSPORT SEND");
        Log.d(TAG, "   Endpoint: " + endpointId);
        Log.d(TAG, "   Transport: " + transport);
        Log.d(TAG, "   Bytes: " + bytes);
    }

    // ✅ Member List Update
    public static void logMemberListUpdate(int totalMembers, int onlineMembers) {
        if (!debugEnabled)
            return;
        Log.d(TAG, "👥 MEMBER LIST UPDATE");
        Log.d(TAG, "   Total: " + totalMembers);
        Log.d(TAG, "   Online: " + onlineMembers);
    }

    // ✅ Error Logging
    public static void logError(String operation, String error, Exception e) {
        Log.e(TAG, "❌ ERROR in " + operation);
        Log.e(TAG, "   Error: " + error);
        if (e != null) {
            Log.e(TAG, "   Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ✅ Connection Events
    public static void logConnection(String deviceId, String deviceName, String type, boolean connected) {
        if (!debugEnabled)
            return;
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, (connected ? "🔵 DEVICE CONNECTED" : "⚫ DEVICE DISCONNECTED"));
        Log.d(TAG, "   ID: " + deviceId);
        Log.d(TAG, "   Name: " + deviceName);
        Log.d(TAG, "   Type: " + type);
        Log.d(TAG, "═══════════════════════════════════════");
    }

    // ✅ Chat Navigation
    public static void logChatOpened(String memberId, String memberName, boolean isPrivate) {
        if (!debugEnabled)
            return;
        Log.d(TAG, "💬 CHAT OPENED");
        Log.d(TAG, "   Type: " + (isPrivate ? "Private" : "Global"));
        if (isPrivate) {
            Log.d(TAG, "   Member ID: " + memberId);
            Log.d(TAG, "   Member Name: " + memberName);
        }
    }

    // Helper
    private static String truncate(String text, int maxLength) {
        if (text == null)
            return "null";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength) + "...";
    }

    // ✅ Print debugging summary
    public static void printDebugSummary(int sentCount, int receivedCount, int unreadTotal) {
        if (!debugEnabled)
            return;
        Log.d(TAG, "\n");
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "📊 MESSAGING DEBUG SUMMARY");
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "   Messages Sent: " + sentCount);
        Log.d(TAG, "   Messages Received: " + receivedCount);
        Log.d(TAG, "   Total Unread: " + unreadTotal);
        Log.d(TAG, "═══════════════════════════════════════");
        Log.d(TAG, "\n");
    }
}
