package com.example.disastercomm.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.disastercomm.MainActivityNew;
import com.example.disastercomm.R;

public class NotificationHelper {

        private static final String CHANNEL_ID_MESSAGES = "channel_messages";
        private static final String CHANNEL_ID_SOS = "channel_sos";
        private static final String CHANNEL_ID_CONNECTIONS = "channel_connections";
        private static final String CHANNEL_ID_NETWORK = "channel_network";
        private static final String CHANNEL_ID_SYSTEM = "channel_system";

        private static final int NOTIFICATION_ID_MSG = 1001;
        private static final int NOTIFICATION_ID_SOS = 1002;
        private static final int NOTIFICATION_ID_CONNECTION = 1003;
        private static final int NOTIFICATION_ID_NETWORK = 1004;
        private static final int NOTIFICATION_ID_SYSTEM = 1005;

        private final Context context;

        public NotificationHelper(Context context) {
                this.context = context;
                createNotificationChannels();
        }

        private void createNotificationChannels() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        NotificationManager manager = context.getSystemService(NotificationManager.class);
                        if (manager != null) {
                                // Message Channel
                                NotificationChannel msgChannel = new NotificationChannel(
                                                CHANNEL_ID_MESSAGES,
                                                "Messages",
                                                NotificationManager.IMPORTANCE_HIGH);
                                msgChannel.setDescription("Notifications for new messages");
                                manager.createNotificationChannel(msgChannel);

                                // SOS Channel
                                NotificationChannel sosChannel = new NotificationChannel(
                                                CHANNEL_ID_SOS,
                                                "Emergency SOS",
                                                NotificationManager.IMPORTANCE_HIGH);
                                sosChannel.setDescription("Emergency alerts and SOS");
                                manager.createNotificationChannel(sosChannel);

                                // Connection Channel
                                NotificationChannel connectionChannel = new NotificationChannel(
                                                CHANNEL_ID_CONNECTIONS,
                                                "Device Connections",
                                                NotificationManager.IMPORTANCE_DEFAULT);
                                connectionChannel.setDescription("Notifications when contacts come online");
                                manager.createNotificationChannel(connectionChannel);

                                // Network Channel
                                NotificationChannel networkChannel = new NotificationChannel(
                                                CHANNEL_ID_NETWORK,
                                                "Network Status",
                                                NotificationManager.IMPORTANCE_LOW);
                                networkChannel.setDescription("Network availability and status changes");
                                manager.createNotificationChannel(networkChannel);

                                // System Channel
                                NotificationChannel systemChannel = new NotificationChannel(
                                                CHANNEL_ID_SYSTEM,
                                                "System Events",
                                                NotificationManager.IMPORTANCE_LOW);
                                systemChannel.setDescription("Application system events and status");
                                manager.createNotificationChannel(systemChannel);
                        }
                }
        }

        public void showMessageNotification(String senderName, String messageBody, String senderId) {
                if (ActivityCompat.checkSelfPermission(context,
                                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        android.util.Log.e("NotificationHelper", "Permission POST_NOTIFICATIONS denied");
                        return;
                }

                Intent intent = new Intent(context, MainActivityNew.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                // Pass extras to open specific chat if needed in future
                intent.putExtra("sender_id", senderId);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                                context, 0, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
                                .setSmallIcon(R.drawable.ic_app_logo) // Ensure this resource exists, or use
                                                                      // android.R.drawable.ic_dialog_email
                                .setContentTitle(senderName)
                                .setContentText(messageBody)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true);

                // Notify
                // Using senderId.hashCode() as notification ID allows multiple notifications
                // from different users
                // or usage of a fixed ID to stack them. Let's use unique ID per sender.
                android.util.Log.d("NotificationHelper", "Showing notification for: " + senderName);
                NotificationManagerCompat.from(context).notify(senderId.hashCode(), builder.build());
        }

        public void showSosNotification(String senderName, String messageBody) {
                if (ActivityCompat.checkSelfPermission(context,
                                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        return;
                }

                Intent intent = new Intent(context, MainActivityNew.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                                context, 0, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_SOS)
                                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                                .setContentTitle("🚨 SOS: " + senderName)
                                .setContentText(messageBody)
                                .setStyle(new NotificationCompat.BigTextStyle().bigText(messageBody))
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setCategory(NotificationCompat.CATEGORY_ALARM)
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true);

                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SOS, builder.build());
        }

        public void showDeviceConnectedNotification(String deviceName, String connectionType) {
                if (ActivityCompat.checkSelfPermission(context,
                                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        return;
                }

                Intent intent = new Intent(context, MainActivityNew.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                                context, 0, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_CONNECTIONS)
                                .setSmallIcon(R.drawable.ic_app_logo)
                                .setContentTitle("Device Connected")
                                .setContentText(deviceName + " via " + connectionType)
                                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true);

                android.util.Log.d("NotificationHelper",
                                "Device connected: " + deviceName + " (" + connectionType + ")");
                NotificationManagerCompat.from(context).notify(deviceName.hashCode(), builder.build());
        }

        public void showDeviceDisconnectedNotification(String deviceName, String connectionType) {
                if (ActivityCompat.checkSelfPermission(context,
                                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        return;
                }

                Intent intent = new Intent(context, MainActivityNew.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                                context, 0, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_CONNECTIONS)
                                .setSmallIcon(R.drawable.ic_app_logo)
                                .setContentTitle("Device Disconnected")
                                .setContentText(deviceName + " (" + connectionType + ")")
                                .setPriority(NotificationCompat.PRIORITY_LOW)
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true);

                android.util.Log.d("NotificationHelper",
                                "Device disconnected: " + deviceName + " (" + connectionType + ")");
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CONNECTION, builder.build());
        }

        public void showNetworkStatusNotification(String status, boolean isAvailable) {
                if (ActivityCompat.checkSelfPermission(context,
                                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        return;
                }

                Intent intent = new Intent(context, MainActivityNew.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                                context, 0, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                String title = isAvailable ? "Network Available" : "Network Lost";
                int icon = isAvailable ? R.drawable.ic_app_logo : android.R.drawable.stat_notify_error;

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_NETWORK)
                                .setSmallIcon(icon)
                                .setContentTitle(title)
                                .setContentText(status)
                                .setPriority(NotificationCompat.PRIORITY_LOW)
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true);

                android.util.Log.d("NotificationHelper", "Network status: " + status);
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_NETWORK, builder.build());
        }

        public void showSystemNotification(String title, String message) {
                if (ActivityCompat.checkSelfPermission(context,
                                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        return;
                }

                Intent intent = new Intent(context, MainActivityNew.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

                PendingIntent pendingIntent = PendingIntent.getActivity(
                                context, 0, intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_SYSTEM)
                                .setSmallIcon(R.drawable.ic_app_logo)
                                .setContentTitle(title)
                                .setContentText(message)
                                .setPriority(NotificationCompat.PRIORITY_LOW)
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true);

                android.util.Log.d("NotificationHelper", title + ": " + message);
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SYSTEM, builder.build());
        }
}
