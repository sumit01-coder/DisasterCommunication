package com.example.disastercomm.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.button.MaterialButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.disastercomm.R;
import com.example.disastercomm.adapters.MembersAdapter;
import com.example.disastercomm.models.MemberItem;

import java.util.ArrayList;
import java.util.List;

public class MembersFragment extends Fragment {

    private RecyclerView rvMembers;
    private TextView tvMeshInfo;
    private MaterialButton btnManualConnect;
    private MembersAdapter membersAdapter;
    private List<MemberItem> members = new ArrayList<>(); // ✅ Restore this

    // Callback interface for manual connection
    private ManualConnectionListener connectionListener; // ✅ Restore listener

    public interface ManualConnectionListener {
        void onManualScanRequested();
    }

    public void setConnectionListener(ManualConnectionListener listener) {
        this.connectionListener = listener;
    }

    // State for merging
    private List<MemberItem> activeMembers = new ArrayList<>();
    private List<com.example.disastercomm.models.User> persistentUsers = new ArrayList<>();

    public MembersFragment() {
        super();
        android.util.Log.d("MembersFragment", "🏗️ CONSTRUCTOR CALLED - Fragment being created");
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        android.util.Log.d("MembersFragment", "🔴 onCreateView called - inflating layout");
        View view = inflater.inflate(R.layout.fragment_members, container, false);
        android.util.Log.d("MembersFragment",
                "✅ Layout inflated successfully, view is " + (view != null ? "NOT NULL" : "NULL"));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        android.util.Log.d("MembersFragment", "📋 onViewCreated called - finding views");

        tvMeshInfo = view.findViewById(R.id.tvMeshInfo);
        rvMembers = view.findViewById(R.id.rvMembers);
        btnManualConnect = view.findViewById(R.id.btnManualConnect);

        android.util.Log.d("MembersFragment", "Views found - tvMeshInfo: " + (tvMeshInfo != null) +
                ", rvMembers: " + (rvMembers != null) +
                ", btnManualConnect: " + (btnManualConnect != null));

        membersAdapter = new MembersAdapter(members);
        rvMembers.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMembers.setAdapter(membersAdapter);

        // Handle member clicks for Direct Messages
        membersAdapter.setOnMemberClickListener(member -> {
            if (requireActivity() instanceof com.example.disastercomm.MainActivityNew) {
                ((com.example.disastercomm.MainActivityNew) requireActivity()).openPrivateChat(member);
            }
        });

        // Manual connection button click
        if (btnManualConnect != null) {
            btnManualConnect.setOnClickListener(v -> triggerManualScan());
        }

        // ✅ Observe Persistent Users (Saved Contacts)
        com.example.disastercomm.data.AppDatabase.getDatabase(requireContext())
                .userDao().getAllUsers().observe(getViewLifecycleOwner(), users -> {
                    persistentUsers = users;
                    mergeAndDisplay();
                });

        updateMeshInfo(0);
    }

    @Override
    public void onResume() {
        super.onResume();
        // ✅ Pull latest members from Activity to ensure sync
        if (requireActivity() instanceof com.example.disastercomm.MainActivityNew) {
            ((com.example.disastercomm.MainActivityNew) requireActivity()).refreshMembersFragment();
        }
    }

    /**
     * ✅ Merge Active (Nearby) and Persistent (History) members
     */
    private void mergeAndDisplay() {
        java.util.Map<String, MemberItem> mergedMap = new java.util.HashMap<>();

        // 1. Add Offline/History members first (Gray)
        for (com.example.disastercomm.models.User user : persistentUsers) {
            MemberItem item = new MemberItem(user.id, user.name);
            item.isOnline = false; // Default to offline
            item.lastActive = "Offline";
            item.signalStrength = "weak";
            mergedMap.put(user.id, item);
        }

        // 2. Overwrite with Active members (Green)
        for (MemberItem active : activeMembers) {
            active.isOnline = true; // Ensure online
            mergedMap.put(active.id, active);
        }

        // 3. Update List
        members.clear();
        members.addAll(mergedMap.values());

        // Sort: Online first, then by name
        java.util.Collections.sort(members, (m1, m2) -> {
            if (m1.isOnline != m2.isOnline)
                return m1.isOnline ? -1 : 1;
            return m1.name.compareToIgnoreCase(m2.name);
        });

        if (membersAdapter != null) {
            membersAdapter.notifyDataSetChanged();
        }
        updateMeshInfo(activeMembers.size());
    }

    private void triggerManualScan() {
        if (!isAdded())
            return;

        // Show scanning feedback with animation
        if (btnManualConnect != null) {
            btnManualConnect.setEnabled(false);
            btnManualConnect.setText("⏳ Scanning Network...");
            btnManualConnect.setIcon(null); // Remove icon during scan
        }

        Toast.makeText(requireContext(), "🔍 Scanning for nearby devices...", Toast.LENGTH_SHORT).show();

        // Trigger scan via callback
        if (connectionListener != null) {
            connectionListener.onManualScanRequested();
        }

        // Re-enable button after 5 seconds with updated text
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isAdded() && btnManualConnect != null) {
                btnManualConnect.setEnabled(true);
                btnManualConnect.setText("🔍 Scan for Nearby Devices");
                // Restore icon
                btnManualConnect.setIcon(androidx.appcompat.content.res.AppCompatResources
                        .getDrawable(requireContext(), android.R.drawable.ic_menu_search));
                Toast.makeText(requireContext(), "✅ Scan complete", Toast.LENGTH_SHORT).show();
            }
        }, 5000);
    }

    public void updateMembers(List<MemberItem> newMembers) {
        // ✅ Always update state, even if view is not ready
        this.activeMembers = new ArrayList<>(newMembers);

        if (isAdded()) {
            requireActivity().runOnUiThread(() -> mergeAndDisplay());
        }
    }

    private void updateMeshInfo(int count) {
        if (tvMeshInfo != null) {
            // ✅ Calculate statistics
            int bluetoothCount = 0;
            int meshCount = 0;
            int onlineCount = 0;

            for (MemberItem member : members) {
                if (member.isOnline) {
                    onlineCount++;
                    if ("Bluetooth".equalsIgnoreCase(member.connectionSource)) {
                        bluetoothCount++;
                    } else {
                        meshCount++;
                    }
                }
            }

            // ✅ Display rich statistics
            String info = onlineCount + " active • ";
            if (meshCount > 0) {
                info += "📡 " + meshCount + " mesh";
            }
            if (bluetoothCount > 0) {
                if (meshCount > 0)
                    info += " • ";
                info += "🔵 " + bluetoothCount + " BT";
            }
            if (onlineCount == 0) {
                info = "No active connections";
            }

            tvMeshInfo.setText(info);
        }
    }
}
