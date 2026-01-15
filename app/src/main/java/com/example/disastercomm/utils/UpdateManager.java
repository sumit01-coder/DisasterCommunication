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
        new CheckUpdateTask().execute();
    }

    private class CheckUpdateTask extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... voids) {
            String result = null;
            try {
                // Use GitHub API (Latest Release)
                String apiUrl = "https://api.github.com/repos/" + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                // ✅ GitHub API requires User-Agent header or it may reject/timeout
                conn.setRequestProperty("User-Agent", "DisasterComm-App");
                conn.setConnectTimeout(15000); // Increased to 15s for slow networks
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
                    Log.e(TAG, "GitHub API: 404 Not Found (Release not published yet or Private Repo)");
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

        @Override
        protected void onPostExecute(String result) {
            if ("OFFLINE".equals(result)) {
                // Don't annoy user with toasts if just offline
                Log.d(TAG, "Skipping update check - offline");
            } else if (result != null && !result.startsWith("ERROR:")) {
                try {
                    JSONObject release = new JSONObject(result);
                    String tagName = release.getString("tag_name"); // e.g., "v1.2.0"
                    String downloadUrl = release.getJSONArray("assets").getJSONObject(0)
                            .getString("browser_download_url");
                    String body = release.optString("body", "No release notes.");

                    String currentVersion = getAppVersion();

                    Log.d(TAG, "Current: " + currentVersion + ", Latest: " + tagName);

                    // Simple string comparison (Assume tag_name starts with 'v')
                    if (!tagName.equalsIgnoreCase(currentVersion) && !tagName.equals("v" + currentVersion)) {
                        if (isSilentCheck) {
                            sendUpdateNotification(tagName, body, downloadUrl);
                        } else {
                            showUpdateDialog(tagName, body, downloadUrl);
                        }
                    } else {
                        if (!isSilentCheck) {
                            Toast.makeText(context, "App is up to date", Toast.LENGTH_SHORT).show();
                        }
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Parsing update failed", e);
                    if (!isSilentCheck) {
                        Toast.makeText(context, "Failed to parse update info", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                String error = (result != null && result.startsWith("ERROR:")) ? result.substring(7) : "Check failed";
                // Only show toast for explicit errors, maybe optional even then
                Log.w(TAG, "Update error: " + error);
                if (!isSilentCheck) {
                    Toast.makeText(context, "Update check failed: " + error, Toast.LENGTH_SHORT).show();
                }
            }
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
                .setPositiveButton("Update Now", (dialog, which) -> new DownloadTask().execute(downloadUrl))
                .setNegativeButton("Later", null)
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

    private class DownloadTask extends AsyncTask<String, Integer, String> {
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage("Downloading update...");
            progressDialog.setIndeterminate(false);
            progressDialog.setMax(100);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            progressDialog.setCancelable(false);
            progressDialog.show();
        }

        @Override
        protected String doInBackground(String... urls) {
            String apkUrl = urls[0];
            try {
                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();

                int fileLength = conn.getContentLength();
                File outputFile = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk");
                if (outputFile.exists())
                    outputFile.delete();

                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(outputFile);

                byte[] data = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    total += count;
                    if (fileLength > 0) {
                        publishProgress((int) (total * 100 / fileLength));
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

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressDialog.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(String path) {
            progressDialog.dismiss();
            if (path != null) {
                installApk(path);
            } else {
                Toast.makeText(context, "Download failed", Toast.LENGTH_SHORT).show();
            }
        }
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
}
