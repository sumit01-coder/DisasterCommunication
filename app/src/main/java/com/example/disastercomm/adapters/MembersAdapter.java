package com.example.disastercomm.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.disastercomm.R;
import com.example.disastercomm.models.MemberItem;

import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.MemberViewHolder> {

    private final List<MemberItem> members;

    public MembersAdapter(List<MemberItem> members) {
        this.members = members;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        MemberItem member = members.get(position);
        holder.bind(member);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMemberClick(member);
            }
        });
    }

    // Interface for click events
    public interface OnMemberClickListener {
        void onMemberClick(MemberItem member);
    }

    private OnMemberClickListener listener;

    public void setOnMemberClickListener(OnMemberClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        TextView tvAvatarInitial;
        View viewOnlineStatus;
        TextView tvName;
        TextView tvConnectionInfo;
        TextView tvLastActive;
        android.widget.ImageView ivSignalStrength;

        MemberViewHolder(View itemView) {
            super(itemView);
            tvAvatarInitial = itemView.findViewById(R.id.tvAvatarInitial);
            viewOnlineStatus = itemView.findViewById(R.id.viewOnlineStatus);
            tvName = itemView.findViewById(R.id.tvName);
            tvConnectionInfo = itemView.findViewById(R.id.tvConnectionInfo);
            tvLastActive = itemView.findViewById(R.id.tvLastActive);
            ivSignalStrength = itemView.findViewById(R.id.ivSignalStrength);
        }

        void bind(MemberItem member) {
            // Avatar initial
            if (tvAvatarInitial != null && member.name != null && !member.name.isEmpty()) {
                tvAvatarInitial.setText(member.name.substring(0, 1).toUpperCase());
            }

            // Member name
            tvName.setText(member.name);

            // Connection info (source + distance)
            String connectionInfo = member.getConnectionIcon() + " ";
            if (member.connectionSource != null) {
                connectionInfo += member.connectionSource;
            } else {
                connectionInfo += "Mesh";
            }
            if (member.distance > 0) {
                connectionInfo += " • " + member.getDistanceText().replace(" away", "");
            }
            tvConnectionInfo.setText(connectionInfo);

            // Last active
            tvLastActive.setText(member.getLastSeenText());

            // Online status indicator
            if (viewOnlineStatus != null) {
                viewOnlineStatus.setVisibility(member.isOnline ? View.VISIBLE : View.GONE);
            }

            // Signal strength
            if (ivSignalStrength != null) {
                ivSignalStrength.setColorFilter(Color.parseColor(member.getSignalBadgeColor()));
            }

            // Dim offline members
            float alpha = member.isOnline ? 1.0f : 0.6f;
            itemView.setAlpha(alpha);
        }
    }
}
