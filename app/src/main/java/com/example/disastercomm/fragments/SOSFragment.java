package com.example.disastercomm.fragments;

import android.content.Context;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.disastercomm.R;
import com.example.disastercomm.intelligence.SmartSOSBuilder;
import com.example.disastercomm.intelligence.SOSPriorityCalculator;
import com.example.disastercomm.intelligence.NetworkEventBlackBox;
import com.example.disastercomm.models.Message;

public class SOSFragment extends Fragment {

    private String selectedCategory = SOSPriorityCalculator.CAT_OTHER;
    private int peopleCount = 1;

    private TextView tvPriorityScore;
    private TextView tvSelectedCategory;
    private TextView tvPeopleCount;

    public static SOSFragment newInstance() {
        return new SOSFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_sos, container, false);

        tvPriorityScore = root.findViewById(R.id.tvPriorityScore);
        tvSelectedCategory = root.findViewById(R.id.tvSelectedCategory);
        tvPeopleCount = root.findViewById(R.id.tvPeopleCount);

        setupCategoryButtons(root);
        setupPeopleCounter(root);
        setupBroadcastButton(root);

        updatePriorityScore();
        return root;
    }

    private void setupCategoryButtons(View root) {
        int[] catIds = {
            R.id.catMedical, R.id.catFire, R.id.catFlood,
            R.id.catTrapped, R.id.catMissing, R.id.catFood, R.id.catOther
        };
        String[] catNames = {
            SOSPriorityCalculator.CAT_MEDICAL, SOSPriorityCalculator.CAT_FIRE,
            SOSPriorityCalculator.CAT_FLOOD, SOSPriorityCalculator.CAT_TRAPPED,
            SOSPriorityCalculator.CAT_MISSING, SOSPriorityCalculator.CAT_FOOD,
            SOSPriorityCalculator.CAT_OTHER
        };

        for (int i = 0; i < catIds.length; i++) {
            final String name = catNames[i];
            View v = root.findViewById(catIds[i]);
            if (v != null) {
                v.setOnClickListener(view -> {
                    selectedCategory = name;
                    tvSelectedCategory.setText(name);
                    updatePriorityScore();
                });
            }
        }
    }

    private void setupPeopleCounter(View root) {
        Button btnDec = root.findViewById(R.id.btnDecPeople);
        Button btnInc = root.findViewById(R.id.btnIncPeople);
        btnDec.setOnClickListener(v -> {
            if (peopleCount > 1) {
                peopleCount--;
                tvPeopleCount.setText(String.valueOf(peopleCount));
                updatePriorityScore();
            }
        });
        btnInc.setOnClickListener(v -> {
            peopleCount++;
            tvPeopleCount.setText(String.valueOf(peopleCount));
            updatePriorityScore();
        });
    }

    private void updatePriorityScore() {
        int battery = getBatteryLevel();
        int score = SOSPriorityCalculator.calculateScore(
                selectedCategory, peopleCount, battery, false, 0);
        tvPriorityScore.setText(String.valueOf(score));
    }

    private void setupBroadcastButton(View root) {
        Button btn = root.findViewById(R.id.btnBroadcastSOS);
        btn.setOnClickListener(v -> broadcastSOS());
    }

    private void broadcastSOS() {
        int battery = getBatteryLevel();
        // Build the SOS message via SmartSOSBuilder
        // In production, get real GPS from PeerLocationManager or FusedLocationProvider
        String userId = com.example.disastercomm.utils.DeviceUtil.getDeviceId(requireContext());
        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String userName = prefs.getString("username", "User");

        Message sos = new SmartSOSBuilder(userId, userName)
                .setCategory(selectedCategory)
                .setPeopleCount(peopleCount)
                .setBatteryLevel(battery)
                .setContent("SOS | " + selectedCategory + " | " + peopleCount + " people | Battery: " + battery + "%")
                .build();

        NetworkEventBlackBox.logSOSCreated(userName, selectedCategory, sos.priorityScore);

        // Deliver to MainActivityNew for transmission
        if (getActivity() instanceof com.example.disastercomm.MainActivityNew) {
            ((com.example.disastercomm.MainActivityNew) getActivity()).broadcastSOSMessage(sos);
        }

        android.widget.Toast.makeText(requireContext(),
                "SOS Broadcast! Priority: " + sos.priorityScore,
                android.widget.Toast.LENGTH_LONG).show();
    }

    private int getBatteryLevel() {
        try {
            android.content.Intent batteryStatus = requireContext().registerReceiver(null,
                    new IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                return (int) ((level / (float) scale) * 100);
            }
        } catch (Exception ignored) {}
        return 80;
    }
}
