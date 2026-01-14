package com.example.disastercomm.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.disastercomm.R;
import com.example.disastercomm.services.LiveLocationService;
import com.example.disastercomm.utils.LiveLocationSharingManager;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/**
 * Bottom sheet fragment for controlling live location sharing
 */
public class LiveLocationControlsFragment extends BottomSheetDialogFragment {

    private LiveLocationSharingManager sharingManager;

    private TextView tvSharingStatus;
    private TextView tvRemainingTime;
    private ChipGroup chipGroupDuration;
    private Button btnStartSharing;
    private Button btnStopSharing;

    private Handler updateHandler;
    private Runnable updateRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_live_location_controls, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        sharingManager = LiveLocationSharingManager.getInstance(requireContext());

        // Initialize views
        tvSharingStatus = view.findViewById(R.id.tvSharingStatus);
        tvRemainingTime = view.findViewById(R.id.tvRemainingTime);
        chipGroupDuration = view.findViewById(R.id.chipGroupDuration);
        btnStartSharing = view.findViewById(R.id.btnStartSharing);
        btnStopSharing = view.findViewById(R.id.btnStopSharing);

        // Set up listeners
        btnStartSharing.setOnClickListener(v -> startSharing());
        btnStopSharing.setOnClickListener(v -> stopSharing());

        // Register sharing manager listener
        sharingManager.addListener(new LiveLocationSharingManager.SharingStatusListener() {
            @Override
            public void onSharingStarted(long duration) {
                if (isAdded()) {
                    updateUI();
                }
            }

            @Override
            public void onSharingStopped() {
                if (isAdded()) {
                    updateUI();
                }
            }

            @Override
            public void onDurationUpdated(long remainingMs) {
                if (isAdded()) {
                    updateRemainingTimeDisplay(remainingMs);
                }
            }
        });

        // Set up periodic UI update
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAdded() && sharingManager.isSharingActive()) {
                    long remaining = sharingManager.getRemainingTime();
                    updateRemainingTimeDisplay(remaining);
                    updateHandler.postDelayed(this, 1000);
                }
            }
        };

        // Initial UI update
        updateUI();

        // Start periodic updates if sharing is active
        if (sharingManager.isSharingActive()) {
            updateHandler.post(updateRunnable);
        }
    }

    private void startSharing() {
        long duration = getSelectedDuration();

        // Start the service
        LiveLocationService.startSharing(requireContext(), duration);

        // Update sharing manager
        sharingManager.startSharing(duration);

        Toast.makeText(requireContext(),
                "Started sharing location for " + LiveLocationSharingManager.formatDuration(duration),
                Toast.LENGTH_SHORT).show();

        // Update UI
        updateUI();

        // Start periodic updates
        updateHandler.post(updateRunnable);
    }

    private void stopSharing() {
        // Stop the service
        LiveLocationService.stopSharing(requireContext());

        // Update sharing manager
        sharingManager.stopSharing();

        Toast.makeText(requireContext(), "Stopped sharing location", Toast.LENGTH_SHORT).show();

        // Update UI
        updateUI();

        // Stop periodic updates
        updateHandler.removeCallbacks(updateRunnable);
    }

    private long getSelectedDuration() {
        int checkedId = chipGroupDuration.getCheckedChipId();

        if (checkedId == R.id.chip15Min) {
            return LiveLocationSharingManager.DURATION_15_MIN;
        } else if (checkedId == R.id.chip30Min) {
            return LiveLocationSharingManager.DURATION_30_MIN;
        } else if (checkedId == R.id.chip1Hour) {
            return LiveLocationSharingManager.DURATION_1_HOUR;
        } else if (checkedId == R.id.chipContinuous) {
            return LiveLocationSharingManager.DURATION_CONTINUOUS;
        }

        return LiveLocationSharingManager.DURATION_15_MIN; // Default
    }

    private void updateUI() {
        boolean isSharing = sharingManager.isSharingActive();

        if (isSharing) {
            tvSharingStatus.setText("Actively sharing");
            tvSharingStatus.setTextColor(getResources().getColor(R.color.connected_green, null));
            btnStartSharing.setVisibility(View.GONE);
            btnStopSharing.setVisibility(View.VISIBLE);
            tvRemainingTime.setVisibility(View.VISIBLE);
            chipGroupDuration.setEnabled(false);

            long remaining = sharingManager.getRemainingTime();
            updateRemainingTimeDisplay(remaining);
        } else {
            tvSharingStatus.setText("Not sharing");
            tvSharingStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark, null));
            btnStartSharing.setVisibility(View.VISIBLE);
            btnStopSharing.setVisibility(View.GONE);
            tvRemainingTime.setVisibility(View.GONE);
            chipGroupDuration.setEnabled(true);
        }
    }

    private void updateRemainingTimeDisplay(long remainingMs) {
        if (tvRemainingTime != null) {
            String timeText = "Time remaining: " + LiveLocationSharingManager.formatRemainingTime(remainingMs);
            tvRemainingTime.setText(timeText);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (updateHandler != null) {
            updateHandler.removeCallbacks(updateRunnable);
        }
    }
}
