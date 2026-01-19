package com.example.disastercomm.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkSpecifier;
import android.net.wifi.aware.AttachCallback;
import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.DiscoverySessionCallback;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.PublishDiscoverySession;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.SubscribeDiscoverySession;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareSession;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Manages Wi-Fi Aware (NAN) connectivity.
 * Implements NAN-DAP (Neighbor Awareness Networking - Discovery And Pairing).
 */
@RequiresApi(api = Build.VERSION_CODES.O)
public class WifiAwareNetworkManager {
    private static final String TAG = "WifiAwareManager";
    private static final String SERVICE_NAME = "DisasterComm_NAN";

    private final Context context;
    private final WifiAwareManager wifiAwareManager;
    private WifiAwareSession awareSession;
    private PublishDiscoverySession publishSession;
    private SubscribeDiscoverySession subscribeSession;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isScanning = false;

    public interface WifiAwareCallback {
        void onNanoDeviceFound(PeerHandle peerHandle, byte[] serviceSpecificInfo);

        void onNanoMessageReceived(PeerHandle peerHandle, byte[] message);
    }

    private final WifiAwareCallback callback;

    public WifiAwareNetworkManager(Context context, WifiAwareCallback callback) {
        this.context = context;
        this.callback = callback;
        this.wifiAwareManager = (WifiAwareManager) context.getSystemService(Context.WIFI_AWARE_SERVICE);
    }

    public boolean isSupported() {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE);
    }

    public void start() {
        if (!isSupported()) {
            Log.e(TAG, "Wi-Fi Aware not supported on this device.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission missing for Wi-Fi Aware.");
            return;
        }

        wifiAwareManager.attach(new AttachCallback() {
            @Override
            public void onAttached(WifiAwareSession session) {
                Log.d(TAG, "Wi-Fi Aware Session Attached");
                awareSession = session;
                startPublishing();
                startSubscribing();
            }

            @Override
            public void onAttachFailed() {
                Log.e(TAG, "Wi-Fi Aware Attach Failed");
            }
        }, handler);
    }

    private final java.util.Set<PeerHandle> knownPeers = java.util.Collections
            .synchronizedSet(new java.util.HashSet<>());

    private void startPublishing() {
        PublishConfig config = new PublishConfig.Builder()
                .setServiceName(SERVICE_NAME)
                .build();

        awareSession.publish(config, new DiscoverySessionCallback() {
            @Override
            public void onPublishStarted(PublishDiscoverySession session) {
                Log.d(TAG, "NAN Publishing Started");
                publishSession = session;
            }

            @Override
            public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
                knownPeers.add(peerHandle); // Track peer
                if (callback != null) {
                    callback.onNanoMessageReceived(peerHandle, message);
                }
            }
        }, handler);
    }

    private void startSubscribing() {
        SubscribeConfig config = new SubscribeConfig.Builder()
                .setServiceName(SERVICE_NAME)
                .build();

        awareSession.subscribe(config, new DiscoverySessionCallback() {
            @Override
            public void onSubscribeStarted(SubscribeDiscoverySession session) {
                Log.d(TAG, "NAN Subscribing Started");
                subscribeSession = session;
            }

            @Override
            public void onServiceDiscovered(PeerHandle peerHandle, byte[] serviceSpecificInfo,
                    List<byte[]> matchFilter) {
                Log.d(TAG, "NAN Service Discovered: " + peerHandle);
                knownPeers.add(peerHandle); // Track peer
                if (callback != null) {
                    callback.onNanoDeviceFound(peerHandle, serviceSpecificInfo);
                }

                // Auto-reply to initiate pairing (DAP)
                sendMessage(peerHandle, "DAP_INIT".getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public void onMessageReceived(PeerHandle peerHandle, byte[] message) {
                knownPeers.add(peerHandle); // Track peer
                if (callback != null) {
                    callback.onNanoMessageReceived(peerHandle, message);
                }
            }
        }, handler);
    }

    public void sendMessage(PeerHandle peerHandle, byte[] message) {
        if (publishSession != null) {
            publishSession.sendMessage(peerHandle, 0, message);
        } else if (subscribeSession != null) {
            subscribeSession.sendMessage(peerHandle, 0, message);
        }
    }

    public void broadcastMessage(byte[] message) {
        broadcastMessage(message, null);
    }

    public void broadcastMessage(byte[] message, PeerHandle excludeHandle) {
        synchronized (knownPeers) {
            for (PeerHandle peer : knownPeers) {
                if (excludeHandle == null || !peer.equals(excludeHandle)) {
                    sendMessage(peer, message);
                }
            }
        }
    }

    public void stop() {
        if (awareSession != null) {
            awareSession.close();
            awareSession = null;
        }
        publishSession = null;
        subscribeSession = null;
        knownPeers.clear();
    }

    public int getPeerCount() {
        return knownPeers.size();
    }
}
