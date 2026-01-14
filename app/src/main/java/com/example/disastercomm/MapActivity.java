package com.example.disastercomm;

import android.os.Bundle;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;

import com.example.disastercomm.utils.LocationHelper;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;

public class MapActivity extends AppCompatActivity {

    private MapView map;
    private LocationHelper locationHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Required by OSMDroid for user agent to identify app
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        setContentView(R.layout.activity_map);

        map = findViewById(R.id.map);

        // Use Google Satellite Hybrid tiles (Satellite + Roads) to match user
        // preference
        // Note: This accesses Google tiles directly. For production, consider Google
        // Maps SDK or a paid Satellite provider.
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

        map.setTileSource(googleSat);
        map.setMultiTouchControls(true);
        map.setTilesScaledToDpi(true); // Sharp text/lines

        locationHelper = new LocationHelper(this);

        // Default start point
        GeoPoint startPoint = new GeoPoint(0.0, 0.0);
        map.getController().setZoom(15.0);
        map.getController().setCenter(startPoint);

        // Fetch real location
        locationHelper.getCurrentLocation((lat, lng) -> {
            GeoPoint realPoint = new GeoPoint(lat, lng);
            map.getController().setCenter(realPoint);

            Marker startMarker = new Marker(map);
            startMarker.setPosition(realPoint);
            startMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            startMarker.setTitle("You are here");

            map.getOverlays().add(startMarker);

            // SIMULATION: Add a connected peer (e.g., Rescue Team) nearby
            GeoPoint peerPoint = new GeoPoint(lat + 0.001, lng + 0.001); // Slightly offset
            Marker peerMarker = new Marker(map);
            peerMarker.setPosition(peerPoint);
            peerMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            peerMarker.setTitle("Rescue Team (Connected)");
            map.getOverlays().add(peerMarker);

            // REAL-TIME PEERS: Add markers for actual connected devices
            for (java.util.Map.Entry<String, GeoPoint> entry : PeerLocationManager.getInstance().getPeerLocations()
                    .entrySet()) {
                Marker realPeerMarker = new Marker(map);
                realPeerMarker.setPosition(entry.getValue());
                realPeerMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                realPeerMarker.setTitle("Peer: " + entry.getKey().substring(0, 4));
                map.getOverlays().add(realPeerMarker);
            }

            map.invalidate(); // Refresh map
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
    }
}
