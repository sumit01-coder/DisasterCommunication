package com.example.disastercomm.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.disastercomm.MainActivity;
import com.example.disastercomm.R;
import com.example.disastercomm.models.Message;
import com.example.disastercomm.network.PacketHandler;
import com.example.disastercomm.utils.DeviceUtil;
import com.example.disastercomm.utils.LiveLocationSharingManager;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Foreground service for continuous live location sharing
 */
public class LiveLocationService extends Service {
    private static final String TAG = "LiveLocationService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "live_location_channel";
    // Tuned for Real-Time Tracking
    private static final long LOCATION_UPDATE_INTERVAL = 5000; // 5 seconds
    private static final long LOCATION_FASTEST_INTERVAL = 2000; // 2 seconds

    public static final String ACTION_START_SHARING = "com.example.disastercomm.START_SHARING";
    public static final String ACTION_STOP_SHARING = "com.example.disastercomm.STOP_SHARING";
    public static final String EXTRA_DURATION = "duration";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LiveLocationSharingManager sharingManager;
    private static PacketHandler staticPacketHandler; // Static reference set by MainActivity
    private NotificationManager notificationManager;
    private Handler updateHandler;
    private Runnable updateRunnable;

    private String username;
    private String deviceId;

    /**
     * Set the PacketHandler instance to use for broadcasting
     * Call this from MainActivity after PacketHandler is initialized
     */
    public static void setPacketHandler(PacketHandler handler) {
        staticPacketHandler = handler;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sharingManager = LiveLocationSharingManager.getInstance(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Load user info
        android.content.SharedPreferences prefs = getSharedPreferences("UserPrefs", MODE_PRIVATE);
        username = prefs.getString("username", "Unknown");
        deviceId = DeviceUtil.getDeviceId(this);

        createNotificationChannel();

        // Handler for periodic notification updates
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateNotification();
                sharingManager.updateDuration();

                // Check if sharing should stop
                if (!sharingManager.isSharingActive()) {
                    stopSelf();
                } else {
                    updateHandler.postDelayed(this, 1000); // Update every second
                }
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();

            if (ACTION_START_SHARING.equals(action)) {
                long duration = intent.getLongExtra(EXTRA_DURATION, 0);
                startLocationSharing(duration);
            } else if (ACTION_STOP_SHARING.equals(action)) {
                stopLocationSharing();
            }
        }

        return START_STICKY;
    }

    private void startLocationSharing(long duration) {
        Log.d(TAG, "Starting location sharing for " + LiveLocationSharingManager.formatDuration(duration));

        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification());

        // Start location updates
        startLocationUpdates();

        // Start notification update timer
        updateHandler.post(updateRunnable);

        // Notify peers via Chat
        if (staticPacketHandler != null) {
            Message startMsg = new Message(deviceId, username, Message.Type.TEXT,
                    "🔴 Started sharing live location. Track me on the map!");
            staticPacketHandler.sendMessage(startMsg);
        }
    }

    private void stopLocationSharing() {
        Log.d(TAG, "Stopping location sharing");

        // Stop location updates
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        // Stop notification updates
        updateHandler.removeCallbacks(updateRunnable);

        // Stop service
        stopForeground(true);
        stopSelf();
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                LOCATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(LOCATION_FASTEST_INTERVAL)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null && locationResult.getLastLocation() != null) {
                    Location location = locationResult.getLastLocation();
                    broadcastLocation(location.getLatitude(), location.getLongitude());
                }
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
            Log.d(TAG, "Location updates started");
        } catch (SecurityException e) {
            Log.e(TAG, "Location permission not granted", e);
            stopLocationSharing();
        }
    }

    private void broadcastLocation(double latitude, double longitude) {
        // Use the static packet handler reference
        if (staticPacketHandler == null) {
            Log.e(TAG, "PacketHandler not available, cannot broadcast location");
            return;
        }

        String locPayload = latitude + "," + longitude;
        Message locMsg = new Message(deviceId, username, Message.Type.LOCATION_UPDATE, locPayload);
        locMsg.isLiveSharing = true;
        locMsg.sharingUntil = sharingManager.getSharingUntilTimestamp();

        staticPacketHandler.sendMessage(locMsg);
        Log.d(TAG, "Broadcast location: " + latitude + ", " + longitude);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Live Location Sharing",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Notifications for live location sharing status");
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        // Intent to open the app
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent to stop sharing
        Intent stopIntent = new Intent(this, LiveLocationService.class);
        stopIntent.setAction(ACTION_STOP_SHARING);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String contentText = getNotificationText();

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sharing Live Location")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_members) // TODO: Create ic_live_location icon
                .setContentIntent(openPendingIntent)
                .addAction(R.drawable.ic_members, "Stop Sharing", stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification() {
        Notification notification = createNotification();
        notificationManager.notify(NOTIFICATION_ID, notification);
    }

    private String getNotificationText() {
        long remaining = sharingManager.getRemainingTime();
        if (remaining == -1) {
            return "Sharing continuously";
        } else if (remaining > 0) {
            return "Time remaining: " + LiveLocationSharingManager.formatRemainingTime(remaining);
        } else {
            return "Stopping...";
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");

        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }

        updateHandler.removeCallbacks(updateRunnable);

        // Update sharing manager
        sharingManager.stopSharing();
    }

    /**
     * Helper method to start the service
     */
    public static void startSharing(Context context, long duration) {
        Intent intent = new Intent(context, LiveLocationService.class);
        intent.setAction(ACTION_START_SHARING);
        intent.putExtra(EXTRA_DURATION, duration);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Helper method to stop the service
     */
    public static void stopSharing(Context context) {
        Intent intent = new Intent(context, LiveLocationService.class);
        intent.setAction(ACTION_STOP_SHARING);
        context.startService(intent);
    }
}
