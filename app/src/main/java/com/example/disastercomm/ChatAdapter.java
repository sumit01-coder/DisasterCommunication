package com.example.disastercomm;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.disastercomm.models.Message;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private final List<Message> messages = new ArrayList<>();
    private final Map<String, Integer> messagePositions = new HashMap<>(); // Track message positions by ID
    private final String myDeviceId;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
    private OnLocationClickListener locationClickListener;

    public interface OnLocationClickListener {
        void onLocationClick(String userId);
    }

    public ChatAdapter(String myDeviceId, OnLocationClickListener locationClickListener) {
        this.myDeviceId = myDeviceId;
        this.locationClickListener = locationClickListener;
    }

    public void addMessage(Message message) {
        // Don't add receipts to UI, just use them to update status
        if (message.type == Message.Type.DELIVERY_RECEIPT || message.type == Message.Type.READ_RECEIPT) {
            return;
        }

        // Prevent duplicates
        if (messagePositions.containsKey(message.id)) {
            return;
        }

        messages.add(message);
        messagePositions.put(message.id, messages.size() - 1);
        notifyItemInserted(messages.size() - 1);
    }

    /**
     * Update message status (for delivery/read receipts)
     */
    public void updateMessageStatus(String messageId, Message.Status newStatus) {
        Integer position = messagePositions.get(messageId);
        if (position != null && position < messages.size()) {
            Message message = messages.get(position);
            message.status = newStatus;

            if (newStatus == Message.Status.DELIVERED) {
                message.deliveredTime = System.currentTimeMillis();
            } else if (newStatus == Message.Status.READ) {
                message.readTime = System.currentTimeMillis();
            }

            notifyItemChanged(position);
        }
    }

    /**
     * ✅ Clear all messages from adapter
     */
    public void clearMessages() {
        int size = messages.size();
        messages.clear();
        messagePositions.clear();
        notifyItemRangeRemoved(0, size);
    }

    @Override
    public int getItemViewType(int position) {
        if (messages.get(position).senderId.equals(myDeviceId)) {
            return TYPE_SENT;
        } else {
            return TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_SENT) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
            return new SentMessageHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Message message = messages.get(position);
        int viewType = getItemViewType(position);

        // Handle location updates specifically
        if (message.type == Message.Type.LOCATION_UPDATE) {
            if (holder instanceof SentMessageHolder) {
                ((SentMessageHolder) holder).bindLocation(message, timeFormat, locationClickListener);
            } else {
                ((ReceivedMessageHolder) holder).bindLocation(message, timeFormat, locationClickListener);
            }
        } else {
            if (holder instanceof SentMessageHolder) {
                ((SentMessageHolder) holder).bind(message, timeFormat, locationClickListener);
            } else {
                ((ReceivedMessageHolder) holder).bind(message, timeFormat, locationClickListener);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class SentMessageHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvTimestamp;
        android.widget.ImageView ivStatus;

        SentMessageHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            ivStatus = itemView.findViewById(R.id.ivStatus);
        }

        void bind(Message message, SimpleDateFormat timeFormat, OnLocationClickListener locationClickListener) {
            tvMessage.setText(message.content);
            tvTimestamp.setText(timeFormat.format(new Date(message.timestamp)));

            // Reset style
            tvMessage.setTextColor(itemView.getResources().getColor(R.color.text_primary, null));
            tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);

            // Highlight SOS messages
            if (message.type == Message.Type.SOS) {
                tvMessage.setTextColor(0xFFFF5722); // Red
                tvMessage.setTypeface(null, android.graphics.Typeface.BOLD);

                // Allow clicking SOS location messages too
                if (message.content.contains("Location:")) {
                    tvMessage.setOnClickListener(v -> {
                        if (locationClickListener != null) {
                            locationClickListener.onLocationClick(message.senderId);
                        }
                    });
                }
            } else {
                tvMessage.setOnClickListener(null); // Reset listener for normal messages
            }

            updateStatus(message);
        }

        void bindLocation(Message message, SimpleDateFormat timeFormat, OnLocationClickListener locationClickListener) {
            tvMessage.setText("📍 Shared Live Location\nTap to view on map");
            tvMessage.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTimestamp.setText(timeFormat.format(new Date(message.timestamp)));

            tvMessage.setOnClickListener(v -> {
                if (locationClickListener != null) {
                    String userId = message.senderId;
                    locationClickListener.onLocationClick(userId);
                }
            });
            updateStatus(message);
        }

        void updateStatus(Message message) {
            if (ivStatus != null) {
                ivStatus.setVisibility(View.VISIBLE);
                switch (message.status) {
                    case SENDING:
                        ivStatus.setImageResource(R.drawable.ic_check); // Single tick
                        ivStatus.setAlpha(0.5f);
                        break;
                    case SENT:
                        ivStatus.setImageResource(R.drawable.ic_check); // Single tick
                        ivStatus.setAlpha(1.0f);
                        ivStatus.setColorFilter(android.graphics.Color.GRAY);
                        break;
                    case DELIVERED:
                        ivStatus.setImageResource(R.drawable.ic_double_check); // Double tick
                        ivStatus.setAlpha(1.0f);
                        ivStatus.setColorFilter(android.graphics.Color.GRAY);
                        break;
                    case READ:
                        ivStatus.setImageResource(R.drawable.ic_double_check); // Double tick
                        ivStatus.setColorFilter(0xFF2196F3); // Blue
                        break;
                    case FAILED:
                        ivStatus.setVisibility(View.GONE); // Or show error icon
                        break;
                }
            }
        }
    }

    static class ReceivedMessageHolder extends RecyclerView.ViewHolder {
        TextView tvSenderName, tvMessage, tvTimestamp;

        ReceivedMessageHolder(View itemView) {
            super(itemView);
            tvSenderName = itemView.findViewById(R.id.tvSenderName);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
        }

        void bind(Message message, SimpleDateFormat timeFormat, OnLocationClickListener locationClickListener) {
            String displayName = (message.senderName != null && !message.senderName.isEmpty())
                    ? message.senderName
                    : message.senderId.substring(0, Math.min(8, message.senderId.length()));

            if (tvSenderName != null) {
                tvSenderName.setText(displayName);
                tvSenderName.setVisibility(View.VISIBLE);
                tvSenderName.setTextColor(itemView.getResources().getColor(R.color.text_secondary, null));
            }

            tvMessage.setText(message.content);
            tvTimestamp.setText(timeFormat.format(new Date(message.timestamp)));

            // Reset style
            tvMessage.setTextColor(itemView.getResources().getColor(R.color.text_primary, null));
            tvMessage.setTypeface(null, android.graphics.Typeface.NORMAL);

            // Highlight SOS messages
            if (message.type == Message.Type.SOS) {
                tvMessage.setTextColor(0xFFFF5722); // Red
                tvMessage.setTypeface(null, android.graphics.Typeface.BOLD);
                if (tvSenderName != null) {
                    tvSenderName.setTextColor(0xFFFF5722);
                    tvSenderName.setText("🚨 SOS from " + displayName);
                }

                if (message.content.contains("Location:")) {
                    tvMessage.setOnClickListener(v -> {
                        if (locationClickListener != null) {
                            locationClickListener.onLocationClick(message.senderId);
                        }
                    });
                }
            } else {
                tvMessage.setOnClickListener(null);
            }
        }

        void bindLocation(Message message, SimpleDateFormat timeFormat, OnLocationClickListener locationClickListener) {
            String displayName = (message.senderName != null && !message.senderName.isEmpty())
                    ? message.senderName
                    : message.senderId.substring(0, Math.min(8, message.senderId.length()));

            if (tvSenderName != null) {
                tvSenderName.setText(displayName);
                tvSenderName.setVisibility(View.VISIBLE);
            }

            tvMessage.setText("📍 Shared Live Location\nTap to view on map");
            tvMessage.setTypeface(null, android.graphics.Typeface.BOLD);
            tvMessage.setTextColor(itemView.getResources().getColor(R.color.primary, null)); // Blue-ish
            tvTimestamp.setText(timeFormat.format(new Date(message.timestamp)));

            tvMessage.setOnClickListener(v -> {
                if (locationClickListener != null) {
                    String userId = message.senderId;
                    locationClickListener.onLocationClick(userId);
                }
            });
        }
    }

    // Removed openMap method as it is no longer used internally
}
