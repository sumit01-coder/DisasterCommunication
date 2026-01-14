package com.example.disastercomm;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.example.disastercomm.models.PeerItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PeersAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;
    private static final long NEW_DEVICE_HIGHLIGHT_DURATION = 5000; // 5 seconds

    public List<PeerItem> items = new ArrayList<>();
    private Set<String> previousDeviceIds = new HashSet<>();

    public void updateList(List<PeerItem> newItems) {
        // Mark new devices
        Set<String> newDeviceIds = new HashSet<>();
        for (PeerItem item : newItems) {
            if (item.type != PeerItem.Type.HEADER) {
                newDeviceIds.add(item.id);
                // Mark as new if not previously seen
                if (!previousDeviceIds.contains(item.id)) {
                    item.isNew = true;
                }
            }
        }

        previousDeviceIds = newDeviceIds;
        this.items = newItems;
        notifyDataSetChanged();

        // Schedule removal of "new" badges after delay
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            for (PeerItem item : items) {
                item.isNew = false;
            }
            notifyDataSetChanged();
        }, NEW_DEVICE_HIGHLIGHT_DURATION);
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type == PeerItem.Type.HEADER ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent,
                    false);
            TextView tv = v.findViewById(android.R.id.text1);
            tv.setTextAppearance(android.R.style.TextAppearance_Material_Subhead);
            tv.setTextColor(ContextCompat.getColor(parent.getContext(), R.color.purple_700));
            tv.setPadding(32, 24, 32, 12);
            return new HeaderViewHolder(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_peer, parent, false);
            return new PeerViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        PeerItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).tvTitle.setText(item.displayName);
        } else if (holder instanceof PeerViewHolder) {
            ((PeerViewHolder) holder).bind(item);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;

        HeaderViewHolder(View v) {
            super(v);
            tvTitle = v.findViewById(android.R.id.text1);
        }
    }

    static class PeerViewHolder extends RecyclerView.ViewHolder {
        CardView cardPeer;
        TextView tvName, tvStatus, tvAddress, tvSignalStrength, tvTypeBadge, tvNewBadge;
        ImageView ivIcon;

        PeerViewHolder(View itemView) {
            super(itemView);
            cardPeer = itemView.findViewById(R.id.cardPeer);
            tvName = itemView.findViewById(R.id.tvPeerName);
            tvStatus = itemView.findViewById(R.id.tvPeerStatus);
            tvAddress = itemView.findViewById(R.id.tvPeerAddress);
            tvSignalStrength = itemView.findViewById(R.id.tvSignalStrength);
            tvTypeBadge = itemView.findViewById(R.id.tvTypeBadge);
            tvNewBadge = itemView.findViewById(R.id.tvNewBadge);
            ivIcon = itemView.findViewById(R.id.ivPeerIcon);
        }

        void bind(PeerItem item) {
            tvName.setText(item.displayName);

            // Show/hide new badge
            if (item.isNew) {
                tvNewBadge.setVisibility(View.VISIBLE);
                // Pulse animation for new devices
                animateNewDevice();
            } else {
                tvNewBadge.setVisibility(View.GONE);
            }

            // Display address if available
            if (item.address != null && !item.address.isEmpty() && item.type == PeerItem.Type.BLUETOOTH) {
                tvAddress.setText(item.address);
                tvAddress.setVisibility(View.VISIBLE);
            } else {
                tvAddress.setVisibility(View.GONE);
            }

            // Configure based on device type
            if (item.type == PeerItem.Type.BLUETOOTH) {
                tvStatus.setText("Bluetooth Connected");
                tvStatus.setTextColor(0xFF1976D2); // Blue
                tvTypeBadge.setText("BT");
                tvTypeBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF1976D2));
                ivIcon.setColorFilter(0xFF1976D2); // Blue icon

                // Show signal strength for Bluetooth if available
                if (item.signalStrength >= 0) {
                    tvSignalStrength.setVisibility(View.VISIBLE);
                    if (item.signalStrength > 70) {
                        tvSignalStrength.setText("📶 Strong");
                        tvSignalStrength.setTextColor(0xFF4CAF50);
                    } else if (item.signalStrength > 40) {
                        tvSignalStrength.setText("📶 Medium");
                        tvSignalStrength.setTextColor(0xFFFF9800);
                    } else {
                        tvSignalStrength.setText("📶 Weak");
                        tvSignalStrength.setTextColor(0xFFFF5722);
                    }
                } else {
                    tvSignalStrength.setVisibility(View.GONE);
                }

            } else { // MESH
                tvStatus.setText("Mesh Network");
                tvStatus.setTextColor(0xFF4CAF50); // Green
                tvTypeBadge.setText("MESH");
                tvTypeBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF9800));
                ivIcon.setColorFilter(0xFFFF9800); // Orange icon
                tvSignalStrength.setVisibility(View.GONE);
            }
        }

        private void animateNewDevice() {
            // Pulse animation on card background
            ObjectAnimator colorAnim = ObjectAnimator.ofObject(
                    cardPeer,
                    "cardBackgroundColor",
                    new ArgbEvaluator(),
                    Color.WHITE,
                    Color.parseColor("#FFF3E0"), // Light orange
                    Color.WHITE);
            colorAnim.setDuration(1000);
            colorAnim.setRepeatCount(2);
            colorAnim.start();
        }
    }
}
