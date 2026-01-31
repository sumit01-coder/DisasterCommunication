package com.example.disastercomm.fragments;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.disastercomm.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;

public class MapFragment extends Fragment {

    private MapView mapView;
    private IMapController mapController;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;

    private TextView tvMeshCount;
    private TextView tvBluetoothCount;
    private TextView tvMessageCount;
    private TextView tvUnreadCount;
    private TextView tvSignalQuality;
    private TextView tvSignalRange;
    private View viewBluetoothDot;

    // GPS Accuracy UI elements
    private TextView tvGpsAccuracyValue;
    private TextView tvGpsAccuracyStatus;
    private TextView tvGpsAccuracyCondition;
    private View viewGpsAccuracyDot;

    private int totalMessages = 0;
    private int unreadMessages = 0;

    // GPS tracking
    private float currentAccuracy = 0f;
    private GeoPoint lastLocation = null;
    private static final long LOCATION_UPDATE_INTERVAL = 1000; // 1 second
    private static final float LOCATION_UPDATE_DISTANCE = 1f; // 1 meter

    // Callback for marker clicks
    public interface OnMapMemberClickListener {
        void onMemberMarkerClick(String userId, String userName);
    }

    private OnMapMemberClickListener memberClickListener;

    public void setOnMapMemberClickListener(OnMapMemberClickListener listener) {
        this.memberClickListener = listener;
    }

    // Zoom controls
    private FloatingActionButton btnZoomIn;
    private FloatingActionButton btnZoomOut;
    private static final double MIN_ZOOM = 3.0;
    private static final double MAX_ZOOM = 20.0;
    private static final double ZOOM_STEP = 1.0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    // Live Status HUD
    private View cardLiveStatus;
    private TextView tvLiveTimer;
    private View btnStopLive;
    private com.example.disastercomm.utils.LiveLocationSharingManager sharingManager;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sharingManager = com.example.disastercomm.utils.LiveLocationSharingManager.getInstance(requireContext());

        // Initialize HUD
        cardLiveStatus = view.findViewById(R.id.cardLiveStatus);
        tvLiveTimer = view.findViewById(R.id.tvLiveTimer);
        btnStopLive = view.findViewById(R.id.btnStopLive);

        btnStopLive.setOnClickListener(v -> {
            com.example.disastercomm.services.LiveLocationService.stopSharing(requireContext());
            sharingManager.stopSharing();
            updateLiveStatusHud();
        });

        // Initialize OSM configuration
        Context ctx = requireContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        Configuration.getInstance().setUserAgentValue("DisasterComm/1.0");

        // Configure tile cache directory
        File cacheDir = new File(ctx.getCacheDir(), "osm");
        Configuration.getInstance().setOsmdroidTileCache(cacheDir);
        Configuration.getInstance().setOsmdroidBasePath(cacheDir);

        // Initialize status TextViews
        tvMeshCount = view.findViewById(R.id.tvMeshCount);
        tvBluetoothCount = view.findViewById(R.id.tvBluetoothCount);
        tvMessageCount = view.findViewById(R.id.tvMessageCount);
        tvUnreadCount = view.findViewById(R.id.tvUnreadCount);
        tvSignalQuality = view.findViewById(R.id.tvSignalQuality);
        tvSignalRange = view.findViewById(R.id.tvSignalRange);
        viewBluetoothDot = view.findViewById(R.id.viewBluetoothDot);

        // Initialize GPS accuracy views
        tvGpsAccuracyValue = view.findViewById(R.id.tvGpsAccuracyValue);
        tvGpsAccuracyStatus = view.findViewById(R.id.tvGpsAccuracyStatus);
        tvGpsAccuracyCondition = view.findViewById(R.id.tvGpsAccuracyCondition);
        viewGpsAccuracyDot = view.findViewById(R.id.viewGpsAccuracyDot);

        // Initialize zoom buttons
        btnZoomIn = view.findViewById(R.id.btnZoomIn);
        btnZoomOut = view.findViewById(R.id.btnZoomOut);

        // Setup zoom button listeners
        // setupZoomControls(); // This method is not defined in the provided context,
        // assuming it's meant to be called here if it exists elsewhere.

        // Setup live location FAB
        FloatingActionButton fabLiveLocation = view.findViewById(R.id.fabLiveLocation);
        if (fabLiveLocation != null) {
            fabLiveLocation.setOnClickListener(v -> showLiveLocationControls());
        }

        // Initialize map
        mapView = view.findViewById(R.id.mapView);
        if (mapView != null) {
            setupMap();
        }

        // Apply animations
        applyEntryAnimations(view);

        // Start real-time update loop
        startMapUpdateLoop();
    }

    private void updateLiveStatusHud() {
        if (sharingManager != null && sharingManager.isSharingActive()) {
            if (cardLiveStatus.getVisibility() != View.VISIBLE) {
                cardLiveStatus.setVisibility(View.VISIBLE);
                // Animate in
                cardLiveStatus.setAlpha(0f);
                cardLiveStatus.setTranslationY(-50f);
                cardLiveStatus.animate().alpha(1f).translationY(0f).setDuration(300).start();
            }
            long remaining = sharingManager.getRemainingTime();
            tvLiveTimer.setText(com.example.disastercomm.utils.LiveLocationSharingManager.formatRemainingTime(remaining)
                    + " remaining");
        } else {
            cardLiveStatus.setVisibility(View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null)
            mapView.onResume();
        if (locationManager != null && isAdded()) {
            try {
                // ... request updates ...
            } catch (SecurityException e) {
            }
        }
        updateLiveStatusHud();
    }

    // Consolidated logic for location updates and self-pulse
    private void handleUserLocationUpdate(Location location) {
        if (location == null)
            return;

        currentAccuracy = location.getAccuracy();
        updateAccuracyDisplay(currentAccuracy);

        // Smooth movement to new location
        GeoPoint newLocation = new GeoPoint(location.getLatitude(), location.getLongitude());

        if (lastLocation != null) {
            // Animate smooth transition
            mapController.animateTo(newLocation, 15.0, 500L);
        } else {
            // First location, just set it
            mapController.setCenter(newLocation);
        }

        lastLocation = newLocation;

        // Maintain optimal zoom for walking
        double currentZoom = mapView.getZoomLevelDouble();
        if (currentZoom < 14.0 || currentZoom > 18.0) {
            mapController.setZoom(16.0);
        }

        // SELF PULSE LOGIC
        if (sharingManager != null && sharingManager.isSharingActive()) {
            com.example.disastercomm.utils.CirclePulseOverlay overlay = pulseOverlays.get("ME");
            if (overlay == null) {
                int color = androidx.core.content.ContextCompat.getColor(requireContext(),
                        android.R.color.holo_green_light);
                overlay = new com.example.disastercomm.utils.CirclePulseOverlay(mapView, newLocation, color, 120f);
                overlay.start();
                pulseOverlays.put("ME", overlay);
                mapView.getOverlays().add(0, overlay);
            } else {
                overlay.setLocation(newLocation);
            }
        } else {
            com.example.disastercomm.utils.CirclePulseOverlay overlay = pulseOverlays.remove("ME");
            if (overlay != null) {
                overlay.stop();
                mapView.getOverlays().remove(overlay);
            }
        }
        mapView.invalidate();
    }

    private final Handler mapUpdateHandler = new Handler(Looper.getMainLooper());
    private final Runnable mapUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isAdded()) {
                refreshMapMarkers();
                mapUpdateHandler.postDelayed(this, 1000); // Update
                                                          // every
                                                          // 1
                                                          // second
            }
        }
    };

    private void startMapUpdateLoop() {
        mapUpdateHandler.post(mapUpdateRunnable);
    }

    private void stopMapUpdateLoop() {
        mapUpdateHandler.removeCallbacks(mapUpdateRunnable);
    }

    /**
     * Pulls latest peer locations from manager and updates map
     */
    private void refreshMapMarkers() {
        com.example.disastercomm.PeerLocationManager manager = com.example.disastercomm.PeerLocationManager
                .getInstance();
        java.util.Map<String, GeoPoint> locations = manager.getPeerLocations();

        java.util.List<com.example.disastercomm.models.MemberItem> members = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, GeoPoint> entry : locations.entrySet()) {
            com.example.disastercomm.models.MemberItem item = new com.example.disastercomm.models.MemberItem(
                    entry.getKey(),
                    "Peer " + entry.getKey().substring(0, Math.min(4, entry.getKey().length())));
            item.latitude = entry.getValue().getLatitude();
            item.longitude = entry.getValue().getLongitude();
            members.add(item);
        }
        updateMembersOnMap(members);
    }

    private void applyEntryAnimations(View view) {
        // Slide down animation for the main status container
        android.view.animation.Animation slideDown = android.view.animation.AnimationUtils
                .loadAnimation(requireContext(), R.anim.slide_in_top);

        // Pop in animation for individual stats
        android.view.animation.Animation popIn = android.view.animation.AnimationUtils.loadAnimation(requireContext(),
                R.anim.pop_in);
        popIn.setStartOffset(200); // Slight delay

        // Animate the status card container (assuming it's the first child of
        // NestedScrollView or similar container)
        // Since we don't have direct ID for the card container in fragment, we can
        // animate the scroll view if present
        // or just animate the key text views

        if (tvMeshCount != null)
            tvMeshCount.startAnimation(popIn);
        if (tvBluetoothCount != null)
            tvBluetoothCount.startAnimation(popIn);
        if (tvMessageCount != null)
            tvMessageCount.startAnimation(popIn);
        if (tvUnreadCount != null)
            tvUnreadCount.startAnimation(popIn);

        // Animate the signal card specially
        android.view.animation.Animation slideInDelay = android.view.animation.AnimationUtils
                .loadAnimation(requireContext(), R.anim.slide_in_top);
        slideInDelay.setStartOffset(100);
        if (tvSignalQuality != null) {
            View signalCard = (View) tvSignalQuality.getParent();
            if (signalCard != null && signalCard instanceof View) {
                ((View) signalCard).startAnimation(slideInDelay);
            }
        }
    }

    private void setupMap() {
        // Configure map view
        // IMPROVEMENT: Use Google Satellite Hybrid tiles (Satellite + Roads)
        org.osmdroid.tileprovider.tilesource.XYTileSource googleSat = new org.osmdroid.tileprovider.tilesource.XYTileSource(
                "Google-Sat",
                0, 19, 256, ".png",
                new String[] {
                        "https://mt0.google.com/vt/lyrs=y&hl=en&x=",
                        "https://mt1.google.com/vt/lyrs=y&hl=en&x=",
                        "https://mt2.google.com/vt/lyrs=y&hl=en&x=",
                        "https://mt3.google.com/vt/lyrs=y&hl=en&x="
                }) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return getBaseUrl() + org.osmdroid.util.MapTileIndex.getX(pMapTileIndex) +
                        "&y=" + org.osmdroid.util.MapTileIndex.getY(pMapTileIndex) +
                        "&z=" + org.osmdroid.util.MapTileIndex.getZoom(pMapTileIndex);
            }
        };
        mapView.setTileSource(googleSat);

        // Enable touch controls
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
        mapView.setTilesScaledToDpi(true); // Sharper tiles

        // Get map controller
        mapController = mapView.getController();
        mapController.setZoom(16.0); // Closer initial zoom

        // Set default location (Delhi)
        GeoPoint defaultLocation = new GeoPoint(28.6139, 77.2090);
        mapController.setCenter(defaultLocation);

        // IMPROVEMENT: Add Compass Overlay with specialized behavior
        org.osmdroid.views.overlay.compass.CompassOverlay compassOverlay = new org.osmdroid.views.overlay.compass.CompassOverlay(
                requireContext(),
                new org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider(requireContext()), mapView) {
            @Override
            public boolean onSingleTapConfirmed(android.view.MotionEvent e, org.osmdroid.views.MapView mapView) {
                // Standard behavior: Reset orientation to North
                boolean handled = super.onSingleTapConfirmed(e, mapView);

                // Added behavior: Also re-center on user if they exist
                if (handled && lastLocation != null) {
                    mapController.animateTo(lastLocation);
                    return true;
                }
                return handled;
            }
        };
        compassOverlay.enableCompass();
        mapView.getOverlays().add(compassOverlay);

        // IMPROVEMENT: Add Scale Bar Overlay
        org.osmdroid.views.overlay.ScaleBarOverlay scaleBarOverlay = new org.osmdroid.views.overlay.ScaleBarOverlay(
                mapView);
        scaleBarOverlay.setCentred(true);
        scaleBarOverlay.setAlignBottom(true);
        scaleBarOverlay.setTextSize(30);
        mapView.getOverlays().add(scaleBarOverlay);

        // IMPROVEMENT: Add Rotation Gesture Overlay
        org.osmdroid.views.overlay.gestures.RotationGestureOverlay rotationGestureOverlay = new org.osmdroid.views.overlay.gestures.RotationGestureOverlay(
                mapView);
        rotationGestureOverlay.setEnabled(true);
        mapView.setMultiTouchControls(true);
        mapView.getOverlays().add(rotationGestureOverlay);

        // Add "You" marker at default location
        Marker youMarker = new Marker(mapView);
        youMarker.setPosition(defaultLocation);
        youMarker.setTitle("You");
        youMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        youMarker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_members));
        mapView.getOverlays().add(youMarker);

        // Add my location overlay with smooth movement
        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), mapView);
        myLocationOverlay.enableMyLocation();
        // myLocationOverlay.enableFollowLocation(); // Optional: Don't force follow to
        // allow panning
        mapView.getOverlays().add(myLocationOverlay);

        // Setup location manager for accuracy tracking
        setupLocationTracking();
    }

    private void setupLocationTracking() {
        try {
            locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);

            LocationListener locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    handleUserLocationUpdate(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                    updateAccuracyDisplay(Float.MAX_VALUE); // Show poor accuracy
                }
            };

            // Request location updates
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (requireContext().checkSelfPermission(
                        android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            LOCATION_UPDATE_INTERVAL,
                            LOCATION_UPDATE_DISTANCE,
                            locationListener);

                    // Get last known location for initial accuracy
                    Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastKnown != null) {
                        currentAccuracy = lastKnown.getAccuracy();
                        updateAccuracyDisplay(currentAccuracy);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateAccuracyDisplay(float accuracyMeters) {
        if (!isAdded() || tvGpsAccuracyValue == null || tvGpsAccuracyStatus == null ||
                tvGpsAccuracyCondition == null || viewGpsAccuracyDot == null) {
            return;
        }

        String accuracyText;
        String statusText;
        String conditionText;
        int colorResId;

        if (accuracyMeters <= 5.0f) {
            // Excellent: ±3 to 5 meters (Open sky)
            accuracyText = "±" + Math.round(accuracyMeters) + "m";
            statusText = "Excellent";
            conditionText = "Open sky";
            colorResId = R.color.accuracy_excellent;
        } else if (accuracyMeters <= 15.0f) {
            // Good: ±5 to 15 meters (City area)
            accuracyText = "±" + Math.round(accuracyMeters) + "m";
            statusText = "Good";
            conditionText = "City area";
            colorResId = R.color.accuracy_good;
        } else if (accuracyMeters <= 50.0f) {
            // Fair: 20-50 meters (Indoor/Weak)
            accuracyText = "±" + Math.round(accuracyMeters) + "m";
            statusText = "Fair";
            conditionText = "Indoor";
            colorResId = R.color.accuracy_fair;
        } else {
            // Poor: 50m+ (Weak signal)
            accuracyText = "±" + Math.round(Math.min(accuracyMeters, 999)) + "m";
            statusText = "Poor";
            conditionText = "Weak signal";
            colorResId = R.color.accuracy_poor;
        }

        // Update UI
        tvGpsAccuracyValue.setText(accuracyText);
        tvGpsAccuracyStatus.setText(statusText);
        tvGpsAccuracyStatus.setTextColor(getResources().getColor(colorResId, null));
        tvGpsAccuracyCondition.setText(conditionText);
        viewGpsAccuracyDot.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(getResources().getColor(colorResId, null)));
    }

    private void setupZoomControls() {
        if (btnZoomIn != null) {
            btnZoomIn.setOnClickListener(v -> {
                if (mapView != null && mapController != null) {
                    double currentZoom = mapView.getZoomLevelDouble();
                    double newZoom = Math.min(currentZoom + ZOOM_STEP, MAX_ZOOM);
                    // UX IMPROVEMENT: Always center on user location when zooming if available
                    if (lastLocation != null) {
                        mapController.animateTo(lastLocation, newZoom, 300L);
                    } else {
                        mapController.animateTo(mapView.getMapCenter(), newZoom, 300L);
                    }
                }
            });
        }

        if (btnZoomOut != null) {
            btnZoomOut.setOnClickListener(v -> {
                if (mapView != null && mapController != null) {
                    double currentZoom = mapView.getZoomLevelDouble();
                    double newZoom = Math.max(currentZoom - ZOOM_STEP, MIN_ZOOM);
                    // UX IMPROVEMENT: Always center on user location when zooming if available
                    if (lastLocation != null) {
                        mapController.animateTo(lastLocation, newZoom, 300L);
                    } else {
                        mapController.animateTo(mapView.getMapCenter(), newZoom, 300L);
                    }
                }
            });
        }
    }

    public void updateMeshStatus(int connectedCount) {
        if (!isAdded() || tvMeshCount == null) {
            return;
        }
        tvMeshCount.setText(connectedCount + " connected");
    }

    public void updateBluetoothStatus(int deviceCount, boolean enabled) {
        // Check if fragment is attached to avoid crash when not visible
        if (!isAdded() || getContext() == null) {
            return;
        }

        if (tvBluetoothCount != null) {
            tvBluetoothCount.setText(deviceCount + " devices");
        }
        if (viewBluetoothDot != null) {
            int color = enabled ? R.color.connected_green : R.color.signal_weak;
            viewBluetoothDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(color, null)));
        }
    }

    public void updateMessageCount(int total, int unread) {
        if (!isAdded()) {
            return;
        }

        this.totalMessages = total;
        this.unreadMessages = unread;

        if (tvMessageCount != null) {
            tvMessageCount.setText(total + " total");
        }
        if (tvUnreadCount != null) {
            tvUnreadCount.setText(unread + " unread");
        }
    }

    public void incrementMessageCount() {
        totalMessages++;
        unreadMessages++;
        updateMessageCount(totalMessages, unreadMessages);
    }

    public void updateSignalQuality(String quality, String range) {
        if (!isAdded()) {
            return;
        }

        if (tvSignalQuality != null) {
            tvSignalQuality.setText(quality);
        }
        if (tvSignalRange != null) {
            tvSignalRange.setText(range);
        }
    }

    private java.util.List<Marker> memberMarkers = new java.util.ArrayList<>();
    private java.util.Map<String, com.example.disastercomm.utils.CirclePulseOverlay> pulseOverlays = new java.util.HashMap<>();

    public void updateMembersOnMap(java.util.List<com.example.disastercomm.models.MemberItem> members) {
        if (!isAdded() || getContext() == null || mapView == null) {
            return;
        }

        // Remove old markers
        for (Marker marker : memberMarkers) {
            mapView.getOverlays().remove(marker);
        }
        memberMarkers.clear();

        // We don't clear pulseOverlays immediately, we update them or remove unused
        // ones
        java.util.Set<String> activeMemberIds = new java.util.HashSet<>();

        com.example.disastercomm.PeerLocationManager locationManager = com.example.disastercomm.PeerLocationManager
                .getInstance();

        for (com.example.disastercomm.models.MemberItem member : members) {
            if (member.latitude == 0 && member.longitude == 0)
                continue;

            activeMemberIds.add(member.id);
            GeoPoint position = new GeoPoint(member.latitude, member.longitude);

            // Check if member is actively sharing live location
            boolean isLiveSharing = locationManager.isPeerLiveSharing(member.id);

            // HANDLE PULSE OVERLAY
            if (isLiveSharing) {
                com.example.disastercomm.utils.CirclePulseOverlay overlay = pulseOverlays.get(member.id);
                if (overlay == null) {
                    // Create new overlay
                    // Color: Semi-transparent Red/Orange
                    int color = androidx.core.content.ContextCompat.getColor(requireContext(),
                            android.R.color.holo_red_light);
                    overlay = new com.example.disastercomm.utils.CirclePulseOverlay(mapView, position, color, 100f);
                    overlay.start();
                    pulseOverlays.put(member.id, overlay);
                    // Add to map (at bottom of overlays stack ideally, but just adding before
                    // marker logic is enough)
                    mapView.getOverlays().add(0, overlay);
                } else {
                    overlay.setLocation(position); // Update position
                }
            } else {
                // Remove overlay if exists
                com.example.disastercomm.utils.CirclePulseOverlay overlay = pulseOverlays.remove(member.id);
                if (overlay != null) {
                    overlay.stop();
                    mapView.getOverlays().remove(overlay);
                }
            }

            // HANDLE MARKER
            Marker marker = new Marker(mapView);
            marker.setPosition(position);

            if (isLiveSharing) {
                marker.setTitle(member.name + " 🔴 LIVE");
                marker.setSubDescription("Updating live...");
            } else {
                marker.setTitle(member.name);
            }

            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_members));

            marker.setOnMarkerClickListener((m, mapView) -> {
                if (memberClickListener != null) {
                    memberClickListener.onMemberMarkerClick(member.id, member.name);
                }
                return true;
            });

            mapView.getOverlays().add(marker);
            memberMarkers.add(marker);
        }

        // Cleanup orphaned overlays (members who left)
        java.util.List<String> toRemove = new java.util.ArrayList<>();
        for (String id : pulseOverlays.keySet()) {
            if (!activeMemberIds.contains(id)) {
                toRemove.add(id);
            }
        }
        for (String id : toRemove) {
            com.example.disastercomm.utils.CirclePulseOverlay overlay = pulseOverlays.remove(id);
            if (overlay != null) {
                overlay.stop();
                mapView.getOverlays().remove(overlay);
            }
        }

        mapView.invalidate();
    }

    /**
     * Show live location controls bottom sheet
     */
    private void showLiveLocationControls() {
        if (!isAdded()) {
            return;
        }
        com.example.disastercomm.fragments.LiveLocationControlsFragment controlsFragment = new com.example.disastercomm.fragments.LiveLocationControlsFragment();
        controlsFragment.show(getParentFragmentManager(), "LiveLocationControls");
    }

    public void focusOnUser(String userId) {
        if (!isAdded() || mapController == null)
            return;

        com.example.disastercomm.PeerLocationManager manager = com.example.disastercomm.PeerLocationManager
                .getInstance();
        org.osmdroid.util.GeoPoint location = manager.getPeerLocation(userId);

        if (location != null) {
            mapController.animateTo(location, 18.0, 1000L); // Zoom in closer
            android.widget.Toast.makeText(getContext(), "Tracking " + userId, android.widget.Toast.LENGTH_SHORT).show();
        } else {
            android.widget.Toast
                    .makeText(getContext(), "Location not available for " + userId, android.widget.Toast.LENGTH_SHORT)
                    .show();
        }
    }

    public void updateMyLocation(double lat, double lng) {
        if (!isAdded() || mapController == null) {
            return;
        }

        GeoPoint myLocation = new GeoPoint(lat, lng);
        // Smooth animation with duration for natural movement
        mapController.animateTo(myLocation, 16.0, 500L);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopMapUpdateLoop();
        if (mapView != null) {
            mapView.onDetach();
        }
    }
}
