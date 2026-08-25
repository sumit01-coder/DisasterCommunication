package com.example.disastercomm.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

/**
 * Helper class to get device location.
 */
public class LocationHelper {

    public interface LocationListener {
        void onLocationReceived(double latitude, double longitude);
    }

    private final FusedLocationProviderClient fusedLocationClient;

    public LocationHelper(Context context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    @SuppressLint("MissingPermission") // Permissions are checked in MainActivity before calling
    public void getCurrentLocation(LocationListener listener) {
        // First try to get the last known location quickly
        fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) {
                listener.onLocationReceived(loc.getLatitude(), loc.getLongitude());
            }
            // Always request fresh location for highest accuracy in the background
            requestNewLocationData(listener);
        }).addOnFailureListener(e -> {
            requestNewLocationData(listener);
        });
    }

    @SuppressLint("MissingPermission")
    private void requestNewLocationData(LocationListener listener) {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMaxUpdates(1) // Single update
                .build();

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null && locationResult.getLastLocation() != null) {
                    Location loc = locationResult.getLastLocation();
                    listener.onLocationReceived(loc.getLatitude(), loc.getLongitude());
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }
}
