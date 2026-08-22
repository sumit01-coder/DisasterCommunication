package com.example.disastercomm.fragments;

import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.disastercomm.R;
import com.example.disastercomm.models.MemberItem;
import com.example.disastercomm.utils.CirclePulseOverlay;
import com.example.disastercomm.utils.LiveLocationSharingManager;
import com.example.disastercomm.PeerLocationManager;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MapFragment extends Fragment {

    private MapView mapView;
    private IMapController mapController;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;

    // UI Elements
    private TextView tvNetworkStatusTitle;
    private TextView tvNetworkStatusDesc;
    private android.widget.ImageView ivNetworkSignal;

    private BottomSheetBehavior<?> behaviorLayers;
    private BottomSheetBehavior<?> behaviorMarkerDetail;
    private BottomSheetBehavior<?> behaviorReport;

    // Marker Details UI
    private TextView tvMarkerIcon;
    private TextView tvMarkerTitle;
    private TextView tvMarkerSubtitle;
    private TextView tvMarkerStatus;
    private TextView tvMarkerSignal;

    private float currentAccuracy = 0f;
    private GeoPoint lastLocation = null;
    private static final long LOCATION_UPDATE_INTERVAL = 1000;
    private static final float LOCATION_UPDATE_DISTANCE = 1f;

    private LiveLocationSharingManager sharingManager;

    private List<Marker> memberMarkers = new ArrayList<>();
    private Map<String, CirclePulseOverlay> pulseOverlays = new HashMap<>();

    // Mock overlays for toggling
    private Polygon safeZone;
    private Polygon dangerZone;
    private Polyline routeOverlay;
    private Marker hospitalMarker;

    public interface OnMapMemberClickListener {
        void onMemberMarkerClick(String userId, String userName);
    }

    private OnMapMemberClickListener memberClickListener;

    public void setOnMapMemberClickListener(OnMapMemberClickListener listener) {
        this.memberClickListener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        
        // Initialize OSM configuration BEFORE inflating layout to prevent blocked tiles
        Context ctx = requireContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));
        // Use a highly specific User-Agent to comply with OSM policies
        Configuration.getInstance().setUserAgentValue(ctx.getPackageName() + "/1.0 (disastercomm@example.com)");

        File cacheDir = new File(ctx.getCacheDir(), "osm_v3");
        Configuration.getInstance().setOsmdroidTileCache(cacheDir);
        Configuration.getInstance().setOsmdroidBasePath(cacheDir);

        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sharingManager = LiveLocationSharingManager.getInstance(requireContext());

        // Bind UI
        tvNetworkStatusTitle = view.findViewById(R.id.tvNetworkStatusTitle);
        tvNetworkStatusDesc = view.findViewById(R.id.tvNetworkStatusDesc);
        ivNetworkSignal = view.findViewById(R.id.ivNetworkSignal);

        View bottomSheetLayers = view.findViewById(R.id.bottomSheetLayers);
        behaviorLayers = BottomSheetBehavior.from(bottomSheetLayers);
        behaviorLayers.setState(BottomSheetBehavior.STATE_HIDDEN);

        View bottomSheetMarkerDetail = view.findViewById(R.id.bottomSheetMarkerDetail);
        behaviorMarkerDetail = BottomSheetBehavior.from(bottomSheetMarkerDetail);
        behaviorMarkerDetail.setState(BottomSheetBehavior.STATE_HIDDEN);

        View bottomSheetReport = view.findViewById(R.id.bottomSheetReport);
        if (bottomSheetReport != null) {
            behaviorReport = BottomSheetBehavior.from(bottomSheetReport);
            behaviorReport.setState(BottomSheetBehavior.STATE_HIDDEN);
        }

        tvMarkerIcon = view.findViewById(R.id.tvMarkerIcon);
        tvMarkerTitle = view.findViewById(R.id.tvMarkerTitle);
        tvMarkerSubtitle = view.findViewById(R.id.tvMarkerSubtitle);
        tvMarkerStatus = view.findViewById(R.id.tvMarkerStatus);
        tvMarkerSignal = view.findViewById(R.id.tvMarkerSignal);

        mapView = view.findViewById(R.id.mapView);
        if (mapView != null) {
            setupMap();
        }

        setupClickListeners(view);
        startMapUpdateLoop();
    }

    private void setupClickListeners(View view) {
        view.findViewById(R.id.btnCenterLocation).setOnClickListener(v -> {
            if (lastLocation != null) {
                mapController.animateTo(lastLocation, 18.0, 500L);
            } else if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
                mapController.animateTo(myLocationOverlay.getMyLocation(), 18.0, 500L);
            }
        });

        view.findViewById(R.id.btnLayers).setOnClickListener(v -> {
            if (behaviorLayers.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                behaviorLayers.setState(BottomSheetBehavior.STATE_HIDDEN);
            } else {
                behaviorMarkerDetail.setState(BottomSheetBehavior.STATE_HIDDEN);
                if (behaviorReport != null) behaviorReport.setState(BottomSheetBehavior.STATE_HIDDEN);
                behaviorLayers.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        view.findViewById(R.id.btnReport).setOnClickListener(v -> {
            if (behaviorReport != null) {
                if (behaviorReport.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    behaviorReport.setState(BottomSheetBehavior.STATE_HIDDEN);
                } else {
                    behaviorLayers.setState(BottomSheetBehavior.STATE_HIDDEN);
                    behaviorMarkerDetail.setState(BottomSheetBehavior.STATE_HIDDEN);
                    behaviorReport.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
            }
        });

        view.findViewById(R.id.btnMarkerRoute).setOnClickListener(v -> {
             Toast.makeText(requireContext(), "Calculating route...", Toast.LENGTH_SHORT).show();
        });

        // Setup Report actions
        setupReportActions(view);

        // Setup Layer Switches
        setupLayerSwitches(view);

        // Setup SOS Button
        View fabSosMap = view.findViewById(R.id.fabSosMap);
        if (fabSosMap != null) {
            fabSosMap.setOnClickListener(v -> showSosDialog());
        }
    }

    private void setupReportActions(View view) {
        View.OnClickListener reportListener = v -> {
            if (lastLocation != null) {
                Marker alertMarker = new Marker(mapView);
                alertMarker.setPosition(lastLocation);
                alertMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                alertMarker.setTitle("User Report");
                alertMarker.setIcon(ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_dialog_alert));
                mapView.getOverlays().add(alertMarker);
                mapView.invalidate();
                Toast.makeText(requireContext(), "Report submitted to Mesh Network", Toast.LENGTH_SHORT).show();
                if (behaviorReport != null) behaviorReport.setState(BottomSheetBehavior.STATE_HIDDEN);
            } else {
                Toast.makeText(requireContext(), "Location unavailable", Toast.LENGTH_SHORT).show();
            }
        };

        View btnMedical = view.findViewById(R.id.btnReportMedical);
        View btnFire = view.findViewById(R.id.btnReportFire);
        View btnFlood = view.findViewById(R.id.btnReportFlood);
        View btnInfra = view.findViewById(R.id.btnReportInfrastructure);

        if (btnMedical != null) btnMedical.setOnClickListener(reportListener);
        if (btnFire != null) btnFire.setOnClickListener(reportListener);
        if (btnFlood != null) btnFlood.setOnClickListener(reportListener);
        if (btnInfra != null) btnInfra.setOnClickListener(reportListener);
    }

    private void setupLayerSwitches(View view) {
        com.google.android.material.switchmaterial.SwitchMaterial swSafeZones = view.findViewById(R.id.swSafeZones);
        com.google.android.material.switchmaterial.SwitchMaterial swDangerZones = view.findViewById(R.id.swDangerZones);
        com.google.android.material.switchmaterial.SwitchMaterial swEvacuationRoutes = view.findViewById(R.id.swEvacuationRoutes);
        com.google.android.material.switchmaterial.SwitchMaterial swHospitals = view.findViewById(R.id.swHospitals);
        com.google.android.material.switchmaterial.SwitchMaterial swNearbyUsers = view.findViewById(R.id.swNearbyUsers);

        if (swSafeZones != null) {
            swSafeZones.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (safeZone != null) {
                    if (isChecked && !mapView.getOverlays().contains(safeZone)) mapView.getOverlays().add(safeZone);
                    else if (!isChecked) mapView.getOverlays().remove(safeZone);
                    mapView.invalidate();
                }
            });
        }

        if (swDangerZones != null) {
            swDangerZones.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (dangerZone != null) {
                    if (isChecked && !mapView.getOverlays().contains(dangerZone)) mapView.getOverlays().add(dangerZone);
                    else if (!isChecked) mapView.getOverlays().remove(dangerZone);
                    mapView.invalidate();
                }
            });
        }

        if (swEvacuationRoutes != null) {
            swEvacuationRoutes.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (routeOverlay != null) {
                    if (isChecked && !mapView.getOverlays().contains(routeOverlay)) mapView.getOverlays().add(routeOverlay);
                    else if (!isChecked) mapView.getOverlays().remove(routeOverlay);
                    mapView.invalidate();
                }
            });
        }

        if (swHospitals != null) {
            swHospitals.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (hospitalMarker != null) {
                    if (isChecked && !mapView.getOverlays().contains(hospitalMarker)) mapView.getOverlays().add(hospitalMarker);
                    else if (!isChecked) mapView.getOverlays().remove(hospitalMarker);
                    mapView.invalidate();
                }
            });
        }

        if (swNearbyUsers != null) {
            swNearbyUsers.setOnCheckedChangeListener((buttonView, isChecked) -> {
                for (Marker m : memberMarkers) {
                    if (isChecked && !mapView.getOverlays().contains(m)) mapView.getOverlays().add(m);
                    else if (!isChecked) mapView.getOverlays().remove(m);
                }
                mapView.invalidate();
            });
        }

        View btnDownloadOffline = view.findViewById(R.id.btnDownloadOffline);
        if (btnDownloadOffline != null) {
            btnDownloadOffline.setOnClickListener(v -> {
                if (behaviorLayers != null) behaviorLayers.setState(BottomSheetBehavior.STATE_HIDDEN);
                Toast.makeText(requireContext(), "Downloading offline tiles for 10km radius...", Toast.LENGTH_LONG).show();
                
                // Simulate download progress
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Offline Map Downloaded Successfully!", Toast.LENGTH_LONG).show();
                    }
                }, 3000);
            });
        }
    }

    private void showSosDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_sos_confirmation, null);
        dialog.setContentView(dialogView);

        TextView tvSosLocation = dialogView.findViewById(R.id.tvSosLocation);
        if (lastLocation != null) {
            tvSosLocation.setText(String.format("%.4f, %.4f", lastLocation.getLatitude(), lastLocation.getLongitude()));
        }

        dialogView.findViewById(R.id.btnCancelSos).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnConfirmSos).setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(requireContext(), "SOS Alert Sent!", Toast.LENGTH_LONG).show();
        });

        dialog.show();
    }

    private void setupMap() {
        // Use CartoDB Positron (Light) map tiles. It is much more reliable, looks cleaner and doesn't block heavily like OSM.
        org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase cartoDbPositron = new org.osmdroid.tileprovider.tilesource.XYTileSource("CartoDBPositron",
                0, 20, 256, ".png", new String[]{
                "https://a.basemaps.cartocdn.com/light_all/",
                "https://b.basemaps.cartocdn.com/light_all/",
                "https://c.basemaps.cartocdn.com/light_all/"
        });
        mapView.setTileSource(cartoDbPositron);
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);
        mapView.setTilesScaledToDpi(true);

        mapController = mapView.getController();
        mapController.setZoom(16.0);

        GeoPoint defaultLocation = new GeoPoint(28.6139, 77.2090);
        mapController.setCenter(defaultLocation);

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        setupLocationTracking();
        
        // Mock Emergency Data
        addMockOverlays();
    }

    private void addMockOverlays() {
        GeoPoint center = lastLocation != null ? lastLocation : new GeoPoint(28.6139, 77.2090);
        updateMockOverlays(center);
    }

    private void updateMockOverlays(GeoPoint center) {
        double lat = center.getLatitude();
        double lon = center.getLongitude();

        if (safeZone == null) {
            safeZone = new Polygon(mapView);
            safeZone.getFillPaint().setColor(Color.argb(50, 0, 255, 0));
            safeZone.getOutlinePaint().setColor(Color.GREEN);
            safeZone.getOutlinePaint().setStrokeWidth(2.0f);
            mapView.getOverlays().add(safeZone);
        }
        List<GeoPoint> safePoints = new ArrayList<>();
        safePoints.add(new GeoPoint(lat + 0.0011, lon + 0.0010));
        safePoints.add(new GeoPoint(lat + 0.0011, lon + 0.0030));
        safePoints.add(new GeoPoint(lat - 0.0009, lon + 0.0030));
        safePoints.add(new GeoPoint(lat - 0.0009, lon + 0.0010));
        safeZone.setPoints(safePoints);

        if (dangerZone == null) {
            dangerZone = new Polygon(mapView);
            dangerZone.getFillPaint().setColor(Color.argb(50, 255, 0, 0));
            dangerZone.getOutlinePaint().setColor(Color.RED);
            dangerZone.getOutlinePaint().setStrokeWidth(2.0f);
            mapView.getOverlays().add(dangerZone);
        }
        List<GeoPoint> dangerPoints = new ArrayList<>();
        dangerPoints.add(new GeoPoint(lat - 0.0029, lon - 0.0010));
        dangerPoints.add(new GeoPoint(lat - 0.0029, lon - 0.0030));
        dangerPoints.add(new GeoPoint(lat - 0.0049, lon - 0.0030));
        dangerPoints.add(new GeoPoint(lat - 0.0049, lon - 0.0010));
        dangerZone.setPoints(dangerPoints);

        if (routeOverlay == null) {
            routeOverlay = new Polyline(mapView);
            routeOverlay.getOutlinePaint().setColor(Color.BLUE);
            routeOverlay.getOutlinePaint().setStrokeWidth(8.0f);
            mapView.getOverlays().add(routeOverlay);
        }
        List<GeoPoint> routePoints = new ArrayList<>();
        routePoints.add(new GeoPoint(lat, lon));
        routePoints.add(new GeoPoint(lat + 0.0006, lon + 0.0010));
        routePoints.add(new GeoPoint(lat + 0.0001, lon + 0.0020));
        routeOverlay.setPoints(routePoints);

        if (hospitalMarker == null) {
            hospitalMarker = new Marker(mapView);
            hospitalMarker.setTitle("City Hospital");
            hospitalMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            hospitalMarker.setOnMarkerClickListener((marker, map) -> {
                showMarkerDetails("🏥", "City Hospital", "150 m away", "Available", "Strong");
                return true;
            });
            mapView.getOverlays().add(hospitalMarker);
        }
        hospitalMarker.setPosition(new GeoPoint(lat + 0.0001, lon + 0.0020));
    }

    private void showMarkerDetails(String icon, String title, String subtitle, String status, String signal) {
        tvMarkerIcon.setText(icon);
        tvMarkerTitle.setText(title);
        tvMarkerSubtitle.setText(subtitle);
        tvMarkerStatus.setText(status);
        tvMarkerSignal.setText(signal);
        
        behaviorLayers.setState(BottomSheetBehavior.STATE_HIDDEN);
        behaviorMarkerDetail.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void setupLocationTracking() {
        try {
            locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
            LocationListener locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    handleUserLocationUpdate(location);
                }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            };

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                if (requireContext().checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, LOCATION_UPDATE_INTERVAL, LOCATION_UPDATE_DISTANCE, locationListener);
                    Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    if (lastKnown != null) {
                        handleUserLocationUpdate(lastKnown);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleUserLocationUpdate(Location location) {
        if (location == null) return;

        currentAccuracy = location.getAccuracy();
        GeoPoint newLocation = new GeoPoint(location.getLatitude(), location.getLongitude());

        if (lastLocation == null) {
            mapController.setCenter(newLocation);
        }
        lastLocation = newLocation;
        updateMockOverlays(newLocation);

        if (sharingManager != null && sharingManager.isSharingActive()) {
            CirclePulseOverlay overlay = pulseOverlays.get("ME");
            if (overlay == null) {
                int color = ContextCompat.getColor(requireContext(), android.R.color.holo_green_light);
                overlay = new CirclePulseOverlay(mapView, newLocation, color, 120f);
                overlay.start();
                pulseOverlays.put("ME", overlay);
                mapView.getOverlays().add(0, overlay);
            } else {
                overlay.setLocation(newLocation);
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
                mapUpdateHandler.postDelayed(this, 1000);
            }
        }
    };

    private void startMapUpdateLoop() {
        mapUpdateHandler.post(mapUpdateRunnable);
    }

    private void stopMapUpdateLoop() {
        mapUpdateHandler.removeCallbacks(mapUpdateRunnable);
    }

    private void refreshMapMarkers() {
        PeerLocationManager manager = PeerLocationManager.getInstance();
        Map<String, GeoPoint> locations = manager.getPeerLocations();

        List<MemberItem> members = new ArrayList<>();
        for (Map.Entry<String, GeoPoint> entry : locations.entrySet()) {
            MemberItem item = new MemberItem(entry.getKey(), "Peer " + entry.getKey().substring(0, Math.min(4, entry.getKey().length())));
            item.latitude = entry.getValue().getLatitude();
            item.longitude = entry.getValue().getLongitude();
            members.add(item);
        }
        updateMembersOnMap(members);
        
        // Update network status
        if (tvNetworkStatusTitle != null && isAdded()) {
            int count = members.size();
            tvNetworkStatusDesc.setText(count + " nearby devices · ~500 m");
        }
    }

    public void focusOnUser(String userId) {
        if (!isAdded() || mapController == null) return;
        PeerLocationManager manager = PeerLocationManager.getInstance();
        GeoPoint location = manager.getPeerLocation(userId);
        if (location != null) {
            mapController.animateTo(location, 18.0, 1000L);
            Toast.makeText(getContext(), "Tracking " + userId, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), "Location not available for " + userId, Toast.LENGTH_SHORT).show();
        }
    }

    public void updateMembersOnMap(List<MemberItem> members) {
        if (!isAdded() || mapView == null) return;

        for (Marker marker : memberMarkers) {
            mapView.getOverlays().remove(marker);
        }
        memberMarkers.clear();

        Set<String> activeMemberIds = new HashSet<>();
        PeerLocationManager manager = PeerLocationManager.getInstance();

        for (MemberItem member : members) {
            if (member.latitude == 0 && member.longitude == 0) continue;

            activeMemberIds.add(member.id);
            GeoPoint position = new GeoPoint(member.latitude, member.longitude);
            boolean isLiveSharing = manager.isPeerLiveSharing(member.id);

            Marker marker = new Marker(mapView);
            marker.setPosition(position);
            marker.setTitle(member.name);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_members));

            marker.setOnMarkerClickListener((m, map) -> {
                showMarkerDetails("👤", member.name, "Nearby user", "Online", "Good");
                if (memberClickListener != null) {
                    memberClickListener.onMemberMarkerClick(member.id, member.name);
                }
                return true;
            });

            mapView.getOverlays().add(marker);
            memberMarkers.add(marker);
        }
        mapView.invalidate();
    }

    // Required Interface methods called from MainActivityNew
    public void updateMeshStatus(int connectedCount) { }
    public void updateBluetoothStatus(int deviceCount, boolean enabled) { }
    public void updateMessageCount(int total, int unread) { }
    public void updateSignalQuality(String quality, String range) { }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopMapUpdateLoop();
        if (mapView != null) mapView.onDetach();
    }
}
