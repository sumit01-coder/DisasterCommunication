package com.example.disastercomm.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Monitors all available network connections and provides callbacks
 * for network state changes across Wi-Fi, Cellular, Bluetooth, etc.
 */
public class NetworkStateMonitor {

    private static final String TAG = "NetworkStateMonitor";

    private final Context context;
    private final ConnectivityManager connectivityManager;
    private NetworkCallback networkCallback;
    private NetworkStateListener listener;

    public interface NetworkStateListener {
        void onNetworkAvailable(int networkType, String networkName);

        void onNetworkLost(int networkType);

        void onInternetAvailable();

        void onInternetLost();
    }

    public static final int NETWORK_WIFI = 1;
    public static final int NETWORK_CELLULAR = 2;
    public static final int NETWORK_BLUETOOTH = 3;
    public static final int NETWORK_ETHERNET = 4;
    public static final int NETWORK_VPN = 5;

    public NetworkStateMonitor(Context context, NetworkStateListener listener) {
        this.context = context;
        this.listener = listener;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void startMonitoring() {
        // Build request for all network types
        NetworkRequest.Builder requestBuilder = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);

        // Request all transport types
        NetworkRequest request = requestBuilder.build();

        networkCallback = new NetworkCallback();

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback);
            Log.d(TAG, "Network monitoring started for all network types");

            // Check current connectivity
            checkCurrentConnectivity();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start network monitoring", e);
        }
    }

    public void stopMonitoring() {
        if (networkCallback != null && connectivityManager != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
                Log.d(TAG, "Network monitoring stopped");
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop network monitoring", e);
            }
        }
    }

    private void checkCurrentConnectivity() {
        if (connectivityManager != null) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                if (capabilities != null) {
                    notifyNetworkAvailable(capabilities);
                }
            }
        }
    }

    private void notifyNetworkAvailable(NetworkCapabilities capabilities) {
        if (listener == null)
            return;

        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            listener.onNetworkAvailable(NETWORK_WIFI, "Wi-Fi");
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            listener.onNetworkAvailable(NETWORK_CELLULAR, "Cellular");
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            listener.onNetworkAvailable(NETWORK_BLUETOOTH, "Bluetooth");
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            listener.onNetworkAvailable(NETWORK_ETHERNET, "Ethernet");
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            listener.onNetworkAvailable(NETWORK_VPN, "VPN");
        }

        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            listener.onInternetAvailable();
        }
    }

    public boolean isConnectedToInternet() {
        if (connectivityManager != null) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            if (activeNetwork != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
                return capabilities != null &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
        }
        return false;
    }

    public boolean hasAnyConnectivity() {
        if (connectivityManager != null) {
            Network activeNetwork = connectivityManager.getActiveNetwork();
            return activeNetwork != null;
        }
        return false;
    }

    private class NetworkCallback extends ConnectivityManager.NetworkCallback {
        @Override
        public void onAvailable(@NonNull Network network) {
            super.onAvailable(network);
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
            if (capabilities != null) {
                notifyNetworkAvailable(capabilities);
                Log.d(TAG, "Network available: " + network);
            }
        }

        @Override
        public void onLost(@NonNull Network network) {
            super.onLost(network);
            Log.d(TAG, "Network lost: " + network);

            // Check if we still have internet
            if (!isConnectedToInternet() && listener != null) {
                listener.onInternetLost();
            }
        }

        @Override
        public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities capabilities) {
            super.onCapabilitiesChanged(network, capabilities);
            notifyNetworkAvailable(capabilities);
            Log.d(TAG, "Network capabilities changed: " + network);
        }
    }
}
