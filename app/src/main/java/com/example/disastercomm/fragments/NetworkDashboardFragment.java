package com.example.disastercomm.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.disastercomm.R;
import com.example.disastercomm.intelligence.CriticalNodeDetector;
import com.example.disastercomm.intelligence.DeadZoneAnalyzer;
import com.example.disastercomm.intelligence.LoRaDeploymentRecommender;
import com.example.disastercomm.intelligence.NetworkEventBlackBox;
import com.example.disastercomm.network.MeshRoutingTable;
import com.example.disastercomm.network.NetworkHealthMonitor;

import java.util.List;
import java.util.Map;

public class NetworkDashboardFragment extends Fragment {

    private TextView tvHealthScore, tvDeviceCount, tvRouteCount, tvRelayCount;
    private TextView tvCriticalNodes, tvDeadZones, tvLoraRec, tvBlackBoxLog;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private static final long REFRESH_INTERVAL_MS = 3000;

    private MeshRoutingTable routingTable;

    public static NetworkDashboardFragment newInstance() {
        return new NetworkDashboardFragment();
    }

    public void setRoutingTable(MeshRoutingTable table) {
        this.routingTable = table;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_network_dashboard, container, false);
        tvHealthScore = root.findViewById(R.id.tvHealthScore);
        tvDeviceCount = root.findViewById(R.id.tvDeviceCount);
        tvRouteCount = root.findViewById(R.id.tvRouteCount);
        tvRelayCount = root.findViewById(R.id.tvRelayCount);
        tvCriticalNodes = root.findViewById(R.id.tvCriticalNodes);
        tvDeadZones = root.findViewById(R.id.tvDeadZones);
        tvLoraRec = root.findViewById(R.id.tvLoraRec);
        tvBlackBoxLog = root.findViewById(R.id.tvBlackBoxLog);
        startRefreshing();
        return root;
    }

    private void startRefreshing() {
        refreshHandler.post(new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    refreshData();
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
                }
            }
        });
    }

    private void refreshData() {
        if (routingTable == null) {
            // Try to get from host activity
            if (getActivity() instanceof com.example.disastercomm.MainActivityNew) {
                routingTable = ((com.example.disastercomm.MainActivityNew) getActivity()).getRoutingTable();
            }
        }

        if (routingTable != null) {
            MeshRoutingTable.NetworkStats stats = routingTable.getStats();
            int healthScore = NetworkHealthMonitor.calculateHealthScore(stats);
            tvHealthScore.setText(healthScore + "%");
            tvHealthScore.setTextColor(healthScore >= 80 ? 0xFF4CAF50 : healthScore >= 50 ? 0xFFFF8800 : 0xFFFF4444);
            tvDeviceCount.setText(stats.neighborCount + " Devices");
            tvRouteCount.setText(stats.routeCount + " Routes");
            tvRelayCount.setText(stats.relayCount + " Relays");

            // Critical nodes
            List<String> critical = CriticalNodeDetector.detectCriticalNodes(routingTable.getNeighbors(), routingTable.getRoutes());
            tvCriticalNodes.setText(critical.isEmpty() ? "None detected" : String.join(", ", critical));

        } else {
            tvHealthScore.setText("--");
            tvDeviceCount.setText("0 Devices");
            tvRouteCount.setText("0 Routes");
            tvRelayCount.setText("0 Relays");
        }

        // Dead zones
        List<DeadZoneAnalyzer.DeadZone> deadZones = DeadZoneAnalyzer.getActiveDeadZones();
        if (deadZones.isEmpty()) {
            tvDeadZones.setText("No dead zones detected");
        } else {
            StringBuilder sb = new StringBuilder();
            for (DeadZoneAnalyzer.DeadZone dz : deadZones) {
                sb.append(String.format("%.4f, %.4f — %s\n", dz.latitude, dz.longitude, dz.description));
            }
            tvDeadZones.setText(sb.toString().trim());
        }

        // LoRa recommendations
        List<LoRaDeploymentRecommender.Recommendation> recs = LoRaDeploymentRecommender.generateRecommendations();
        if (recs.isEmpty()) {
            tvLoraRec.setText("No recommendations yet");
        } else {
            StringBuilder sb = new StringBuilder();
            for (LoRaDeploymentRecommender.Recommendation r : recs) {
                sb.append(r.toDisplayString()).append("\n\n");
            }
            tvLoraRec.setText(sb.toString().trim());
        }

        // Black box log
        List<NetworkEventBlackBox.Event> events = NetworkEventBlackBox.getEvents();
        if (events.isEmpty()) {
            tvBlackBoxLog.setText("Waiting for events...");
        } else {
            StringBuilder sb = new StringBuilder();
            int start = Math.max(0, events.size() - 30); // last 30 events
            for (int i = events.size() - 1; i >= start; i--) {
                sb.append(events.get(i).toDisplayString()).append("\n");
            }
            tvBlackBoxLog.setText(sb.toString());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        refreshHandler.removeCallbacksAndMessages(null);
    }
}
