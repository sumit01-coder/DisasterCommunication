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
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.views.overlay.MapEventsOverlay;
import com.example.disastercomm.models.Message;
import com.example.disastercomm.network.PacketHandler;

import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MapFragment extends Fragment {

    private MapView mapView;
    private IMapController mapController;
    private MyLocationNewOverlay myLocationOverlay;
    private LocationManager locationManager;

    // UI Elements
    private TextView tvNetworkStatusTitle;
    private TextView tvNetworkStatusDesc;

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
    private long locationUpdateInterval = 1000; // default 1s
    private static final float LOCATION_UPDATE_DISTANCE = 1f;

    private LiveLocationSharingManager sharingManager;

    private List<Marker> memberMarkers = new ArrayList<>();
    private Map<String, CirclePulseOverlay> pulseOverlays = new HashMap<>();

    // Mock overlays for toggling
    private Polygon safeZone;
    private Polygon dangerZone;
    private Polyline routeOverlay;

    private List<Marker> activeHospitalMarkers = new ArrayList<>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean hospitalsFetched = false;
    private List<Marker> activeHazardMarkers = new ArrayList<>();

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

        View layoutHospitalSearch = view.findViewById(R.id.layoutHospitalSearch);
        com.google.android.material.textfield.TextInputEditText etHospitalRadius = view.findViewById(R.id.etHospitalRadius);
        com.google.android.material.button.MaterialButton btnSearchHospitals = view.findViewById(R.id.btnSearchHospitals);

        if (swHospitals != null) {
            swHospitals.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (layoutHospitalSearch != null) layoutHospitalSearch.setVisibility(View.VISIBLE);
                    if (!hospitalsFetched) {
                        int radiusKm = 25;
                        try {
                            radiusKm = Integer.parseInt(etHospitalRadius.getText().toString());
                        } catch (Exception e) {}
                        fetchNearbyHospitals(radiusKm * 1000);
                    } else {
                        for (Marker m : activeHospitalMarkers) {
                            if (!mapView.getOverlays().contains(m)) mapView.getOverlays().add(m);
                        }
                        mapView.invalidate();
                    }
                } else {
                    if (layoutHospitalSearch != null) layoutHospitalSearch.setVisibility(View.GONE);
                    for (Marker m : activeHospitalMarkers) {
                        mapView.getOverlays().remove(m);
                    }
                    mapView.invalidate();
                }
            });
        }
        
        if (btnSearchHospitals != null) {
            btnSearchHospitals.setOnClickListener(v -> {
                int radiusKm = 25;
                try {
                    radiusKm = Integer.parseInt(etHospitalRadius.getText().toString());
                } catch (Exception e) {}
                for (Marker m : activeHospitalMarkers) {
                    mapView.getOverlays().remove(m);
                }
                activeHospitalMarkers.clear();
                mapView.invalidate();
                
                fetchNearbyHospitals(radiusKm * 1000);
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
                
                if (lastLocation == null) {
                    Toast.makeText(requireContext(), "Waiting for location to determine download area...", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                Toast.makeText(requireContext(), "Starting offline map download (10km radius)...", Toast.LENGTH_LONG).show();
                
                try {
                    org.osmdroid.tileprovider.cachemanager.CacheManager cacheManager = new org.osmdroid.tileprovider.cachemanager.CacheManager(mapView);
                    
                    double radiusMeters = 10000; // 10km
                    GeoPoint northEast = lastLocation.destinationPoint(radiusMeters, 45);
                    GeoPoint southWest = lastLocation.destinationPoint(radiusMeters, 225);
                    org.osmdroid.util.BoundingBox bbox = new org.osmdroid.util.BoundingBox(northEast.getLatitude(), northEast.getLongitude(), southWest.getLatitude(), southWest.getLongitude());
                    
                    cacheManager.downloadAreaAsync(requireContext(), bbox, 13, 16, new org.osmdroid.tileprovider.cachemanager.CacheManager.CacheManagerCallback() {
                        @Override
                        public void onTaskComplete() {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(), "Offline Map Downloaded Successfully!", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                        @Override
                        public void onTaskFailed(int errors) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(), "Download finished with " + errors + " errors. (Some tiles may have failed)", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                        @Override
                        public void updateProgress(int progress, int currentZoomLevel, int zoomMin, int zoomMax) {}
                        @Override
                        public void downloadStarted() {}
                        @Override
                        public void setPossibleTilesInArea(int total) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (isAdded()) {
                                    Toast.makeText(requireContext(), "Found " + total + " tiles to download. Please wait...", Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(requireContext(), "Failed to start download: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
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
        
        // Prevent ViewPager2 from intercepting horizontal swipes on the map
        mapView.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            return false;
        });

        mapController = mapView.getController();
        mapController.setZoom(16.0);

        GeoPoint defaultLocation = new GeoPoint(28.6139, 77.2090);
        mapController.setCenter(defaultLocation);

        myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(requireContext()), mapView);
        myLocationOverlay.enableMyLocation();
        mapView.getOverlays().add(myLocationOverlay);

        boolean isLowPower = requireContext().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).getBoolean("low_power_mode", false);
        if (isLowPower) {
            locationUpdateInterval = 30000; // 30 seconds
            // Invert map colors for dark mode (save battery on OLED)
            float[] colorMatrixInvert = {
                -1.0f, 0.0f,  0.0f,  0.0f,  255f, // red
                0.0f,  -1.0f, 0.0f,  0.0f,  255f, // green
                0.0f,  0.0f,  -1.0f, 0.0f,  255f, // blue
                0.0f,  0.0f,  0.0f,  1.0f,  0.0f  // alpha
            };
            mapView.getOverlayManager().getTilesOverlay().setColorFilter(new android.graphics.ColorMatrixColorFilter(colorMatrixInvert));
        }

        setupLocationTracking();
        
        // Setup Map Events for Long Press
        MapEventsReceiver mReceive = new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                return false;
            }
            @Override
            public boolean longPressHelper(GeoPoint p) {
                showHazardDialog(p);
                return true;
            }
        };
        MapEventsOverlay overlayEvents = new MapEventsOverlay(mReceive);
        mapView.getOverlays().add(overlayEvents);

        // Mock Emergency Data
        addMockOverlays();
    }

    private void showHazardDialog(GeoPoint location) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_map_marker, null);
        dialog.setContentView(view);

        View.OnClickListener clickListener = v -> {
            String emoji = "🚨";
            String title = "Hazard";
            int id = v.getId();
            if (id == R.id.cardMarkerFlood) { emoji = "🌊"; title = "Flood / High Water"; }
            else if (id == R.id.cardMarkerFire) { emoji = "🔥"; title = "Fire / Smoke"; }
            else if (id == R.id.cardMarkerSafe) { emoji = "🛡️"; title = "Safe Zone"; }
            else if (id == R.id.cardMarkerBlocked) { emoji = "🚧"; title = "Blocked Road"; }

            String hazardId = java.util.UUID.randomUUID().toString();
            PeerLocationManager.getInstance().addHazard(hazardId, emoji, title, location.getLatitude(), location.getLongitude());
            
            // Broadcast via Intent to MainActivity
            String username = requireContext().getSharedPreferences("UserPrefs", android.content.Context.MODE_PRIVATE).getString("username", "Unknown");
            String payload = emoji + "|" + title + "|" + location.getLatitude() + "|" + location.getLongitude();
            
            android.content.Intent intent = new android.content.Intent("com.example.disastercomm.SEND_MESH_MESSAGE");
            intent.setPackage(requireContext().getPackageName());
            intent.putExtra("type", "MAP_MARKER");
            intent.putExtra("content", payload);
            requireContext().sendBroadcast(intent);
            
            Toast.makeText(requireContext(), title + " marker dropped and broadcasted!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            refreshMapMarkers();
        };

        view.findViewById(R.id.cardMarkerFlood).setOnClickListener(clickListener);
        view.findViewById(R.id.cardMarkerFire).setOnClickListener(clickListener);
        view.findViewById(R.id.cardMarkerSafe).setOnClickListener(clickListener);
        view.findViewById(R.id.cardMarkerBlocked).setOnClickListener(clickListener);

        dialog.show();
    }

    private void addMockOverlays() {
        GeoPoint center = lastLocation != null ? lastLocation : new GeoPoint(28.6139, 77.2090);
        updateMockOverlays(center);
    }

    private void updateMockOverlays(GeoPoint center) {
        // Add some mock nearby users for testing the UI
        PeerLocationManager manager = PeerLocationManager.getInstance();
        if (manager.getPeerLocations().isEmpty()) {
            manager.updatePeerLocation("MOCK1", center.getLatitude() + 0.005, center.getLongitude() + 0.005);
            manager.updatePeerRole("MOCK1", "RESCUE");

            manager.updatePeerLocation("MOCK2", center.getLatitude() - 0.003, center.getLongitude() - 0.004);
            manager.updatePeerRole("MOCK2", "MEDICAL");

            manager.updatePeerLocation("MOCK3", center.getLatitude() + 0.008, center.getLongitude() - 0.002);
            manager.updatePeerRole("MOCK3", "VOLUNTEER");
        }
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

    private void fetchNearbyHospitals(int radiusMeters) {
        if (lastLocation == null) {
            Toast.makeText(requireContext(), "Location not available yet", Toast.LENGTH_SHORT).show();
            if (getView() != null) {
                com.google.android.material.switchmaterial.SwitchMaterial swHospitals = getView().findViewById(R.id.swHospitals);
                if (swHospitals != null) swHospitals.setChecked(false);
            }
            return;
        }

        Toast.makeText(requireContext(), "Searching for hospitals within " + (radiusMeters/1000) + "km...", Toast.LENGTH_SHORT).show();
        double lat = lastLocation.getLatitude();
        double lon = lastLocation.getLongitude();
        
        executorService.execute(() -> {
            try {
                // Overpass API Query: Find hospitals within requested radius
                String query = "[out:json];(node[\"amenity\"=\"hospital\"](around:" + radiusMeters + "," + lat + "," + lon + ");way[\"amenity\"=\"hospital\"](around:" + radiusMeters + "," + lat + "," + lon + "););out center;";
                String urlString = "https://overpass-api.de/api/interpreter?data=" + java.net.URLEncoder.encode(query, "UTF-8");
                URL url = new URL(urlString);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                JsonObject jsonResponse = JsonParser.parseReader(reader).getAsJsonObject();
                JsonArray elements = jsonResponse.getAsJsonArray("elements");
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded()) return;
                    hospitalsFetched = true;
                    if (elements.size() == 0) {
                        Toast.makeText(requireContext(), "No hospitals found within " + (radiusMeters/1000) + "km", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<GeoPoint> points = new ArrayList<>();
                    if (lastLocation != null) points.add(lastLocation);

                    for (JsonElement el : elements) {
                        JsonObject obj = el.getAsJsonObject();
                        double hLat = obj.has("lat") ? obj.get("lat").getAsDouble() : (obj.has("center") ? obj.getAsJsonObject("center").get("lat").getAsDouble() : 0);
                        double hLon = obj.has("lon") ? obj.get("lon").getAsDouble() : (obj.has("center") ? obj.getAsJsonObject("center").get("lon").getAsDouble() : 0);
                        
                        if (hLat == 0 && hLon == 0) continue;

                        String name = "Hospital";
                        if (obj.has("tags")) {
                            JsonObject tags = obj.getAsJsonObject("tags");
                            if (tags.has("name")) {
                                name = tags.get("name").getAsString();
                            }
                        }
                        
                        GeoPoint point = new GeoPoint(hLat, hLon);
                        points.add(point);
                        
                        Marker hospitalMarker = new Marker(mapView);
                        hospitalMarker.setPosition(point);
                        hospitalMarker.setTitle(name);
                        hospitalMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        hospitalMarker.setIcon(androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.ic_hospital_new));
                        final String finalName = name;
                        hospitalMarker.setOnMarkerClickListener((marker, map) -> {
                            showMarkerDetails("🏥", finalName, "Nearby Hospital", "Available", "Network: Normal");
                            return true;
                        });
                        
                        activeHospitalMarkers.add(hospitalMarker);
                        mapView.getOverlays().add(hospitalMarker);
                    }
                    mapView.invalidate();

                    if (points.size() > 1) {
                        org.osmdroid.util.BoundingBox boundingBox = org.osmdroid.util.BoundingBox.fromGeoPoints(points);
                        mapView.zoomToBoundingBox(boundingBox, true, 200);
                    }

                    Toast.makeText(requireContext(), "Found " + elements.size() + " hospitals", Toast.LENGTH_SHORT).show();
                });
                
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Failed to fetch hospitals. Check internet connection.", Toast.LENGTH_SHORT).show();
                        if (getView() != null) {
                            com.google.android.material.switchmaterial.SwitchMaterial swHospitals = getView().findViewById(R.id.swHospitals);
                            if (swHospitals != null) swHospitals.setChecked(false);
                        }
                        hospitalsFetched = false;
                    }
                });
            }
        });
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
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, locationUpdateInterval, LOCATION_UPDATE_DISTANCE, locationListener);
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
            String peerId = entry.getKey();
            MemberItem item = new MemberItem(peerId, "Peer " + peerId.substring(0, Math.min(4, peerId.length())));
            item.latitude = entry.getValue().getLatitude();
            item.longitude = entry.getValue().getLongitude();
            item.role = manager.getPeerRole(peerId);
            members.add(item);
        }
        updateMembersOnMap(members);
        updateHazardsOnMap();
        
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

        boolean showNearbyUsers = true;
        if (getView() != null) {
            com.google.android.material.switchmaterial.SwitchMaterial sw = getView().findViewById(R.id.swNearbyUsers);
            if (sw != null) showNearbyUsers = sw.isChecked();
        }

        for (MemberItem member : members) {
            if (member.latitude == 0 && member.longitude == 0) continue;

            activeMemberIds.add(member.id);
            GeoPoint position = new GeoPoint(member.latitude, member.longitude);
            boolean isLiveSharing = manager.isPeerLiveSharing(member.id);

            Marker marker = new Marker(mapView);
            marker.setPosition(position);
            marker.setTitle(member.name);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            
            int iconRes = R.drawable.ic_members;
            String emojiIcon = "👤";
            String subtitle = "Nearby user";
            
            if ("RESCUE".equals(member.role)) {
                iconRes = R.drawable.ic_role_rescue;
                emojiIcon = "🚨";
                subtitle = "Rescue Worker";
            } else if ("MEDICAL".equals(member.role)) {
                iconRes = R.drawable.ic_role_medical;
                emojiIcon = "🏥";
                subtitle = "Medical Professional";
            } else if ("VOLUNTEER".equals(member.role)) {
                iconRes = R.drawable.ic_role_volunteer;
                emojiIcon = "🤝";
                subtitle = "Community Volunteer";
            }
            
            marker.setIcon(ContextCompat.getDrawable(requireContext(), iconRes));

            final String fEmoji = emojiIcon;
            final String fSub = subtitle;
            marker.setOnMarkerClickListener((m, map) -> {
                showMarkerDetails(fEmoji, member.name, fSub, "Online", "Good");
                if (memberClickListener != null) {
                    memberClickListener.onMemberMarkerClick(member.id, member.name);
                }
                return true;
            });

            if (showNearbyUsers) {
                mapView.getOverlays().add(marker);
            }
            memberMarkers.add(marker);
        }
        mapView.invalidate();
    }

    private void updateHazardsOnMap() {
        if (!isAdded() || mapView == null) return;
        
        for (Marker m : activeHazardMarkers) {
            mapView.getOverlays().remove(m);
        }
        activeHazardMarkers.clear();

        Map<String, PeerLocationManager.Hazard> hazards = PeerLocationManager.getInstance().getHazards();
        for (Map.Entry<String, PeerLocationManager.Hazard> entry : hazards.entrySet()) {
            PeerLocationManager.Hazard h = entry.getValue();
            Marker marker = new Marker(mapView);
            marker.setPosition(h.location);
            marker.setTitle(h.type + " " + h.title);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            
            // We'll use a generic alert and rely on the text/emoji
            marker.setIcon(ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_dialog_alert));

            marker.setOnMarkerClickListener((m, map) -> {
                showMarkerDetails(h.type, h.title, "Crowdsourced Hazard", "Active", "By Mesh Network");
                return true;
            });
            
            activeHazardMarkers.add(marker);
            mapView.getOverlays().add(marker);
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
