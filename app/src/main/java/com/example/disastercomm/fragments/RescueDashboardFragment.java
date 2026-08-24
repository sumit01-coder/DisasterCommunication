package com.example.disastercomm.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.disastercomm.R;
import com.example.disastercomm.models.Message;

import java.util.ArrayList;
import java.util.List;

public class RescueDashboardFragment extends Fragment {

    private RecyclerView rvIncidents;
    private TextView tvIncidentCount;
    private IncidentAdapter adapter;
    private final List<Message> incidents = new ArrayList<>();

    public static RescueDashboardFragment newInstance() {
        return new RescueDashboardFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_rescue_dashboard, container, false);
        tvIncidentCount = root.findViewById(R.id.tvIncidentCount);
        rvIncidents = root.findViewById(R.id.rvIncidents);
        rvIncidents.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new IncidentAdapter(incidents);
        rvIncidents.setAdapter(adapter);
        return root;
    }

    /**
     * Add or update an SOS incident on the dashboard.
     * Called from MainActivityNew when a new SOS message arrives.
     */
    public void addOrUpdateIncident(Message sosMsg) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            // Avoid duplicates by message ID
            for (int i = 0; i < incidents.size(); i++) {
                if (incidents.get(i).id.equals(sosMsg.id)) {
                    incidents.set(i, sosMsg);
                    adapter.notifyItemChanged(i);
                    sortIncidents();
                    return;
                }
            }
            incidents.add(sosMsg);
            sortIncidents();
            adapter.notifyDataSetChanged();
            tvIncidentCount.setText(incidents.size() + " Active Incidents");
        });
    }

    private void sortIncidents() {
        incidents.sort((a, b) -> Integer.compare(b.priorityScore, a.priorityScore));
        adapter.notifyDataSetChanged();
        tvIncidentCount.setText(incidents.size() + " Active Incidents");
    }

    // ─── Incident Adapter ──────────────────────────────────────────────────────

    static class IncidentAdapter extends RecyclerView.Adapter<IncidentAdapter.VH> {

        enum MissionStatus { REPORTED, ASSIGNED, EN_ROUTE, REACHED, RESOLVED }

        private final List<Message> data;
        private final List<MissionStatus> statuses;

        IncidentAdapter(List<Message> data) {
            this.data = data;
            this.statuses = new ArrayList<>();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_rescue_incident, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Message msg = data.get(pos);
            // ensure status list keeps up
            while (statuses.size() <= pos) statuses.add(MissionStatus.REPORTED);
            MissionStatus status = statuses.get(pos);

            h.tvPriorityBadge.setText(String.valueOf(msg.priorityScore));
            h.tvCategory.setText(msg.emergencyCategory.isEmpty() ? "SOS" : msg.emergencyCategory);
            h.tvSenderName.setText(msg.senderName);
            h.tvPeopleCount.setText(msg.peopleCount + " people");
            h.tvBatteryLevel.setText(msg.batteryLevel >= 0 ? msg.batteryLevel + "%" : "--");
            h.tvStatus.setText(status.name());
            h.tvStatus.setTextColor(statusColor(status));

            long minAgo = (System.currentTimeMillis() - msg.timestamp) / 60000;
            h.tvTimeAgo.setText(minAgo + " min ago");

            // Badge color
            int badgeBg = msg.priorityScore >= 80 ? 0xFFCC0000 :
                         msg.priorityScore >= 60 ? 0xFFFF8800 : 0xFF607D8B;
            h.tvPriorityBadge.setBackgroundColor(badgeBg);

            h.btnAcceptMission.setOnClickListener(v -> {
                int idx = h.getAdapterPosition();
                if (idx != RecyclerView.NO_ID && idx < statuses.size()) {
                    MissionStatus current = statuses.get(idx);
                    MissionStatus next = advanceStatus(current);
                    statuses.set(idx, next);
                    notifyItemChanged(idx);
                }
            });

            h.btnAcceptMission.setText(nextActionLabel(status));
        }

        private int statusColor(MissionStatus s) {
            switch (s) {
                case REPORTED: return 0xFF4CAF50;
                case ASSIGNED: return 0xFF2196F3;
                case EN_ROUTE: return 0xFFFF8800;
                case REACHED: return 0xFFE91E63;
                case RESOLVED: return 0xFF607D8B;
                default: return 0xFFCCDDEE;
            }
        }

        private MissionStatus advanceStatus(MissionStatus s) {
            switch (s) {
                case REPORTED: return MissionStatus.ASSIGNED;
                case ASSIGNED: return MissionStatus.EN_ROUTE;
                case EN_ROUTE: return MissionStatus.REACHED;
                case REACHED: return MissionStatus.RESOLVED;
                default: return MissionStatus.RESOLVED;
            }
        }

        private String nextActionLabel(MissionStatus s) {
            switch (s) {
                case REPORTED: return "Accept Mission";
                case ASSIGNED: return "Mark En Route";
                case EN_ROUTE: return "Mark Reached";
                case REACHED: return "Mark Resolved";
                default: return "Resolved";
            }
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvPriorityBadge, tvCategory, tvTimeAgo, tvSenderName,
                     tvPeopleCount, tvBatteryLevel, tvStatus;
            android.widget.Button btnAcceptMission;

            VH(@NonNull View v) {
                super(v);
                tvPriorityBadge = v.findViewById(R.id.tvPriorityBadge);
                tvCategory = v.findViewById(R.id.tvCategory);
                tvTimeAgo = v.findViewById(R.id.tvTimeAgo);
                tvSenderName = v.findViewById(R.id.tvSenderName);
                tvPeopleCount = v.findViewById(R.id.tvPeopleCount);
                tvBatteryLevel = v.findViewById(R.id.tvBatteryLevel);
                tvStatus = v.findViewById(R.id.tvStatus);
                btnAcceptMission = v.findViewById(R.id.btnAcceptMission);
            }
        }
    }
}
