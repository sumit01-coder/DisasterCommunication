package com.example.disastercomm.utils;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    // TODO: REPLACE WITH YOUR GITHUB REPO DETAILS
    private static final String GITHUB_OWNER = "sumit01-coder";
    private static final String GITHUB_REPO = "DisasterCommunication";

    // SharedPreferences keys for version tracking
    private static final String PREF_NAME = "UpdatePreferences";
    private static final String PREF_LAST_DISMISSED_VERSION = "last_dismissed_version";
    private static final String PREF_LAST_NOTIFIED_VERSION = "last_notified_version";

    private final Context context;

    public UpdateManager(Context context) {
        this.context = context;
    }

    private boolean isSilentCheck = false;
    private static final String NOTIFICATION_CHANNEL_ID = "update_channel";

    public void checkForUpdates() {
        checkForUpdates(false);
    }

    public void checkForUpdates(boolean isSilent) {
        this.isSilentCheck = isSilent;
        if (!isSilent) {
            Toast.makeText(context, "Checking for updates...", Toast.LENGTH_SHORT).show();
        }
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        executor.execute(() -> {
            String result = performCheckUpdate();
            handler.post(() -> onCheckUpdateResult(result));
        });
    }

    private String performCheckUpdate() {
        String result = null;
        try {
            String apiUrl = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "DisasterComm-App");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                result = sb.toString();
            } else if (conn.getResponseCode() == 404) {
                Log.e(TAG, "GitHub API: 404 Not Found");
                return "ERROR: Release not found (404). Check GitHub Actions.";
            } else {
                Log.e(TAG, "GitHub API Response: " + conn.getResponseCode());
            }
        } catch (java.net.UnknownHostException | java.net.SocketTimeoutException e) {
            Log.w(TAG, "Update check skipped (offline/timeout): " + e.getMessage());
            return "OFFLINE";
        } catch (Exception e) {
            Log.e(TAG, "Update check failed", e);
            return "ERROR: " + e.getMessage();
        }
        return result;
    }

    private void onCheckUpdateResult(String result) {
        if ("OFFLINE".equals(result)) {
            Log.d(TAG, "Skipping update check - offline");
        } else if (result != null && !result.startsWith("ERROR:")) {
            try {
                JSONObject release = new JSONObject(result);
                String tagName = release.getString("tag_name");
                String downloadUrl = release.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");
                String body = release.optString("body", "No release notes.");
                String currentVersion = getAppVersion();

                if (isVersionNewer(tagName, currentVersion)) {
                    String lastDismissedVersion = getLastDismissedVersion();
                    // Only ignore dismissed versions if this is a SILENT background check.
                    // If the user manually clicked "Check for Updates", show it even if previously dismissed!
                    if (isSilentCheck && lastDismissedVersion != null &&
                            (tagName.equalsIgnoreCase(lastDismissedVersion) || tagName.equals("v" + lastDismissedVersion))) {
                        Log.d(TAG, "Update " + tagName + " was already dismissed by user (silent check aborted)");
                        return;
                    }
                    saveLastNotifiedVersion(tagName);
                    if (isSilentCheck) sendUpdateNotification(tagName, body, downloadUrl);
                    else showUpdateDialog(tagName, body, downloadUrl);
                } else {
                    if (!isSilentCheck) Toast.makeText(context, "App is up to date", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Parsing update failed", e);
                if (!isSilentCheck) Toast.makeText(context, "Failed to parse update info", Toast.LENGTH_SHORT).show();
            }
        } else {
            String error = (result != null && result.startsWith("ERROR:")) ? result.substring(7) : "Check failed";
            Log.w(TAG, "Update error: " + error);
            if (!isSilentCheck) Toast.makeText(context, "Update check failed: " + error, Toast.LENGTH_SHORT).show();
        }
    }

    private String getAppVersion() {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "Unknown";
        }
    }

    private void showUpdateDialog(String version, String notes, String downloadUrl) {
        new AlertDialog.Builder(context)
                .setTitle("New Update Available!")
                .setMessage("Version: " + version + "\n\n" + notes)
                .setPositiveButton("Update Now", (dialog, which) -> executeDownload(downloadUrl))
                .setNegativeButton("Later", (dialog, which) -> {
                    // ✅ Mark this version as dismissed so we don't show it again
                    markVersionAsDismissed(version);
                    Log.d(TAG, "Update " + version + " dismissed by user");
                })
                .show();
    }

    private void sendUpdateNotification(String version, String notes, String downloadUrl) {
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Software Updates",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        // PendingIntent to launch SettingsActivity (or directly trigger update, but
        // settings is safer/simpler for now)
        // Ideally, we could show the dialog directly, but starting activities from
        // background is restricted.
        // So let's open SettingsActivity where they can click "Check for Updates"
        // manually, OR we launch a translucent activity.
        // For simplicity, let's open SettingsActivity for now, or even better, send a
        // broadcast or open the dialog if app is in foreground.
        // Let's just open the app to the main screen or settings.

        Intent intent = new Intent(context, com.example.disastercomm.SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                context, 0, intent, android.app.PendingIntent.FLAG_ONE_SHOT | android.app.PendingIntent.FLAG_IMMUTABLE);

        android.app.Notification.Builder builder;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder = new android.app.Notification.Builder(context, NOTIFICATION_CHANNEL_ID);
        } else {
            builder = new android.app.Notification.Builder(context);
        }

        builder.setContentTitle("New Update Available: " + version)
                .setContentText("Tap to update to the latest version.")
                .setSmallIcon(android.R.drawable.stat_sys_download) // Use app icon or generic download
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        notificationManager.notify(1001, builder.build());
    }

    public void executeDownload(String apkUrl) {
        ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setMessage("Downloading update...");
        progressDialog.setIndeterminate(false);
        progressDialog.setMax(100);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setCancelable(false);
        progressDialog.show();

        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        executor.execute(() -> {
            String path = performDownload(apkUrl, progressDialog, handler);
            handler.post(() -> {
                progressDialog.dismiss();
                if (path != null) {
                    clearDismissedVersion();
                    installApk(path);
                } else {
                    Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private String performDownload(String apkUrl, ProgressDialog progressDialog, android.os.Handler handler) {
        try {
            URL url = new URL(apkUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.connect();

            int fileLength = conn.getContentLength();
            File outputFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk");
            if (outputFile.exists()) outputFile.delete();

            InputStream input = conn.getInputStream();
            FileOutputStream output = new FileOutputStream(outputFile);

            byte[] data = new byte[4096];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;
                if (fileLength > 0) {
                    int progress = (int) (total * 100 / fileLength);
                    handler.post(() -> progressDialog.setProgress(progress));
                }
                output.write(data, 0, count);
            }
            output.close();
            input.close();
            return outputFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "Download failed", e);
            return null;
        }
    }

    public interface VersionCallback {
        void onVersionFetched(String version, boolean isNewer);

        void onError(String error);
    }

    public void fetchLatestVersion(VersionCallback callback) {
        new Thread(() -> {
            try {
                String apiUrl = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "DisasterComm-App");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }

                    JSONObject release = new JSONObject(sb.toString());
                    String tagName = release.getString("tag_name");
                    String currentVersion = getAppVersion();

                    // Logic to check if newer
                    boolean isNewer = isVersionNewer(tagName, currentVersion);

                    // o. Check if this version was already dismissed
                    if (isNewer) {
                        String lastDismissedVersion = getLastDismissedVersion();
                        if (lastDismissedVersion != null &&
                                (tagName.equalsIgnoreCase(lastDismissedVersion)
                                        || tagName.equals("v" + lastDismissedVersion))) {
                            // User already dismissed this version
                            isNewer = false;
                            Log.d(TAG, "Badge check: Update " + tagName + " was already dismissed");
                        }
                    }

                    final boolean finalIsNewer = isNewer;
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context)
                                .runOnUiThread(() -> callback.onVersionFetched(tagName, finalIsNewer));
                    }
                } else {
                    int responseCode = conn.getResponseCode();
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context)
                                .runOnUiThread(() -> callback.onError("GitHub API: " + responseCode));
                    }
                }
            } catch (Exception e) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> callback.onError(e.getMessage()));
                }
            }
        }).start();
    }

    private void installApk(String path) {
        File file = new File(path);
        if (!file.exists())
            return;

        // On Android 8.0+ (API 26), we need to check if we can install packages
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.getPackageManager().canRequestPackageInstalls()) {
                // Request permission
                Intent permissionIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                permissionIntent.setData(Uri.parse("package:" + context.getPackageName()));
                permissionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(permissionIntent);
                Toast.makeText(context, "Please allow 'Install Unknown Apps' to continue", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(
                FileProvider.getUriForFile(context, context.getPackageName() + ".provider", file),
                "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }

    // ============================================================
    // SharedPreferences Helper Methods for Version Tracking
    // ============================================================

    private boolean isVersionNewer(String latestTag, String currentVersion) {
        try {
            String latest = latestTag.replace("v", "").replace("V", "");
            String current = currentVersion.replace("v", "").replace("V", "");
            
            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");
            
            int length = Math.max(latestParts.length, currentParts.length);
            for (int i = 0; i < length; i++) {
                int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
                int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
                if (latestPart < currentPart) return false;
                if (latestPart > currentPart) return true;
            }
        } catch (Exception e) {
            // Fallback for non-standard version names
            return !latestTag.equalsIgnoreCase(currentVersion) && !latestTag.equals("v" + currentVersion);
        }
        return false;
    }

    /**
     * Get the last version that was dismissed by the user
     */
    private String getLastDismissedVersion() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_LAST_DISMISSED_VERSION, null);
    }

    /**
     * Save the last version that was notified to the user
     */
    private void saveLastNotifiedVersion(String version) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_LAST_NOTIFIED_VERSION, version).apply();
    }

    /**
     * Mark a version as dismissed by the user (clicked "Later")
     */
    private void markVersionAsDismissed(String version) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_LAST_DISMISSED_VERSION, version).apply();
    }

    /**
     * Clear the dismissed version (called when user installs the update)
     */
    private void clearDismissedVersion() {
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(PREF_LAST_DISMISSED_VERSION).apply();
        Log.d(TAG, "Cleared dismissed version - user is installing update");
    }
}
