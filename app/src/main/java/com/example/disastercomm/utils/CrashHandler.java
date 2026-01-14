package com.example.disastercomm.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";
    private final Context context;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.context = context;
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    public static void init(Context context) {
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(context));
    }

    @Override
    public void uncaughtException(@NonNull Thread thread, @NonNull Throwable throwable) {
        Log.e(TAG, "Uncaught Exception detected!", throwable);

        saveCrashReport(thread, throwable);

        // Delegate to default handler to crash the app properly
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable);
        } else {
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        }
    }

    private void saveCrashReport(Thread thread, Throwable throwable) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String fileName = "crash_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date())
                + ".log";

        StringBuilder report = new StringBuilder();
        report.append("************ CRASH REPORT ************\n");
        report.append("Time: ").append(timestamp).append("\n");
        report.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        report.append("Android Version: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT)
                .append(")\n");

        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            report.append("App Version: ").append(pInfo.versionName).append(" (").append(pInfo.versionCode)
                    .append(")\n");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        report.append("\nthread: ").append(thread.getName()).append("\n");
        report.append("Exception: ").append(throwable.toString()).append("\n");
        report.append("Stack Trace:\n");

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        report.append(sw.toString());
        report.append("\n**************************************\n\n");

        File logDir = new File(context.getExternalFilesDir(null), "crash_logs");
        if (!logDir.exists()) {
            if (!logDir.mkdirs()) {
                Log.e(TAG, "Failed to create crash log directory");
                return;
            }
        }

        File logFile = new File(logDir, fileName);
        try (FileWriter writer = new FileWriter(logFile)) {
            writer.write(report.toString());
            Log.i(TAG, "Crash report saved to: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to write crash report", e);
        }
    }
}
