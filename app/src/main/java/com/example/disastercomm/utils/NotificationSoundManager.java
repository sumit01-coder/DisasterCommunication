package com.example.disastercomm.utils;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

/**
 * Handles notification sounds and vibrations for messages
 */
public class NotificationSoundManager {

    private static final String TAG = "NotificationSound";
    private final Context context;
    private MediaPlayer messagePlayer;
    private MediaPlayer sosPlayer;

    public NotificationSoundManager(Context context) {
        this.context = context;
    }

    /**
     * Play sound for regular text message
     */
    public void playMessageSound() {
        try {
            if (messagePlayer == null) {
                // Use system notification sound
                messagePlayer = MediaPlayer.create(context,
                        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI);
            }

            if (messagePlayer != null && !messagePlayer.isPlaying()) {
                messagePlayer.setOnCompletionListener(mp -> mp.seekTo(0));
                messagePlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to play message sound", e);
        }
    }

    /**
     * Play sound for SOS alert (more urgent)
     */
    public void playSosSound() {
        try {
            if (sosPlayer == null) {
                // Use system alarm sound for urgency
                sosPlayer = MediaPlayer.create(context,
                        android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI);
            }

            if (sosPlayer != null && !sosPlayer.isPlaying()) {
                sosPlayer.setOnCompletionListener(mp -> mp.seekTo(0));
                sosPlayer.setLooping(false);
                sosPlayer.start();
            }

            // Also vibrate for SOS
            vibrate(1000); // 1 second vibration
        } catch (Exception e) {
            Log.e(TAG, "Failed to play SOS sound", e);
        }
    }

    /**
     * Vibrate device
     */
    public void vibrate(long duration) {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(duration);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to vibrate", e);
        }
    }

    /**
     * Play subtle sound for delivery receipt
     */
    public void playDeliverySound() {
        try {
            vibrate(50); // Short vibration
        } catch (Exception e) {
            Log.e(TAG, "Failed to play delivery sound", e);
        }
    }

    /**
     * Release media players
     */
    public void release() {
        if (messagePlayer != null) {
            messagePlayer.release();
            messagePlayer = null;
        }
        if (sosPlayer != null) {
            sosPlayer.release();
            sosPlayer = null;
        }
    }
}
