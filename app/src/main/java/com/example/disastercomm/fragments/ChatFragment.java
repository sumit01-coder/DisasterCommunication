package com.example.disastercomm.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView; // ✅ TextView import

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.disastercomm.ChatAdapter;
import com.example.disastercomm.R;
import com.example.disastercomm.models.Message;
import com.example.disastercomm.network.PacketHandler;
import com.example.disastercomm.utils.DeviceUtil;
import com.example.disastercomm.utils.MessageCache; // ✅ Message cache
import com.example.disastercomm.utils.MessageDebugHelper; // ✅ Debug helper

import java.util.List; // ✅ List import

public class ChatFragment extends Fragment implements ChatAdapter.OnLocationClickListener {

    private RecyclerView rvMessages;
    private EditText etMessage;
    private View btnSend;
    private ChatAdapter chatAdapter;
    private PacketHandler packetHandler;
    private String username;
    private String recipientId; // Null for global chat
    private String recipientName;
    private MessageCache messageCache; // ✅ Message cache
    private String myId; // ✅ My device ID
    private View chatHeader; // ✅ Chat header for private messages
    private com.example.disastercomm.models.MemberItem currentRecipient; // ✅ Full member object for status

    private View layoutChannelList;
    private View layoutChannelDetail;
    private View cardGlobalBroadcast;
    private android.widget.ImageView btnBackToChannels;
    private com.example.disastercomm.adapters.MembersAdapter privateChatsAdapter; // ✅ Member adapter for private chats

    public void setRecipient(String id, String name) {
        this.recipientId = id;
        this.recipientName = name;
        // If we don't have the full object yet, we'll try to get it later or just use
        // what we have
        android.util.Log.d("ChatFragment", "📝 Recipient set (Legacy): " + name + " (" + id + ")");
    }

    public void setRecipient(com.example.disastercomm.models.MemberItem member) {
        this.currentRecipient = member;
        this.recipientId = member.id;
        this.recipientName = member.name;
        android.util.Log.d("ChatFragment", "📝 Recipient set (Rich): " + member.name);

        if (isAdded() && isVisible()) {
            updateChatHeader();
            
            // Switch view to chat detail
            if (layoutChannelList != null && layoutChannelDetail != null) {
                layoutChannelList.setVisibility(View.GONE);
                layoutChannelDetail.setVisibility(View.VISIBLE);
            }
            
            if (etMessage != null) {
                etMessage.setHint("Message " + recipientName + "...");
            }
            
            reloadChatHistory();
        }
    }

    public String getRecipientId() {
        return recipientId;
    }

    /**
     * ✅ Force reload chat history (called when opening member chat)
     */
    public void reloadChatHistory() {
        android.util.Log.d("ChatFragment", "🔄 RELOADING chat history for: " + recipientName);
        loadChatHistory();
    }

    public static ChatFragment newInstance(PacketHandler packetHandler, String username) {
        ChatFragment fragment = new ChatFragment();
        fragment.packetHandler = packetHandler;
        fragment.username = username;
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        layoutChannelList = view.findViewById(R.id.layoutChannelList);
        layoutChannelDetail = view.findViewById(R.id.layoutChannelDetail);
        cardGlobalBroadcast = view.findViewById(R.id.cardGlobalBroadcast);
        btnBackToChannels = view.findViewById(R.id.btnBackToChannels);

        // Auto-scroll to last message when keyboard opens
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            boolean keyboardVisible = insets.isVisible(
                    androidx.core.view.WindowInsetsCompat.Type.ime());
            if (keyboardVisible && rvMessages != null) {
                rvMessages.postDelayed(() -> {
                    int count = rvMessages.getAdapter() != null
                            ? rvMessages.getAdapter().getItemCount() : 0;
                    if (count > 0) rvMessages.smoothScrollToPosition(count - 1);
                }, 100);
            }
            return androidx.core.view.ViewCompat.onApplyWindowInsets(v, insets);
        });
        
        TextView tvMyChannelsHeader = view.findViewById(R.id.tvMyChannelsHeader);
        TextView tvDirectMessagesHeader = view.findViewById(R.id.tvDirectMessagesHeader);
        RecyclerView rvPrivateChats = view.findViewById(R.id.rvPrivateChats);
        com.google.android.material.tabs.TabLayout tabLayout = view.findViewById(R.id.tabLayout);
        
        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                    if (tab.getPosition() == 0) { // Channels
                        if (tvMyChannelsHeader != null) tvMyChannelsHeader.setVisibility(View.VISIBLE);
                        if (cardGlobalBroadcast != null) cardGlobalBroadcast.setVisibility(View.VISIBLE);
                        if (tvDirectMessagesHeader != null) tvDirectMessagesHeader.setVisibility(View.GONE);
                        if (rvPrivateChats != null) rvPrivateChats.setVisibility(View.GONE);
                    } else if (tab.getPosition() == 1) { // Groups
                        if (tvMyChannelsHeader != null) tvMyChannelsHeader.setVisibility(View.GONE);
                        if (cardGlobalBroadcast != null) cardGlobalBroadcast.setVisibility(View.GONE);
                        if (tvDirectMessagesHeader != null) tvDirectMessagesHeader.setVisibility(View.GONE);
                        if (rvPrivateChats != null) rvPrivateChats.setVisibility(View.GONE);
                    } else if (tab.getPosition() == 2) { // Direct
                        if (tvMyChannelsHeader != null) tvMyChannelsHeader.setVisibility(View.GONE);
                        if (cardGlobalBroadcast != null) cardGlobalBroadcast.setVisibility(View.GONE);
                        if (tvDirectMessagesHeader != null) tvDirectMessagesHeader.setVisibility(View.VISIBLE);
                        if (rvPrivateChats != null) rvPrivateChats.setVisibility(View.VISIBLE);
                    }
                }
                @Override public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
                @Override public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {}
            });
            // Trigger default state
            tabLayout.selectTab(tabLayout.getTabAt(0));
        }

        View fabNewChat = view.findViewById(R.id.fabNewChat);
        if (fabNewChat != null) {
            fabNewChat.setOnClickListener(v -> {
                if (tabLayout != null) {
                    tabLayout.selectTab(tabLayout.getTabAt(2)); // Switch to Direct tab
                }
            });
        }

        // UI Toggles
        cardGlobalBroadcast.setOnClickListener(v -> {
            // Open global broadcast
            switchToGlobalChat();
            layoutChannelList.setVisibility(View.GONE);
            layoutChannelDetail.setVisibility(View.VISIBLE);
        });

        btnBackToChannels.setOnClickListener(v -> {
            layoutChannelDetail.setVisibility(View.GONE);
            layoutChannelList.setVisibility(View.VISIBLE);
        });

        // Chat UI elements
        etMessage = view.findViewById(R.id.etMessage);
        btnSend = view.findViewById(R.id.btnSend);
        rvMessages = view.findViewById(R.id.rvMessages);
        chatHeader = view.findViewById(R.id.chatHeader); // ✅ Initialize header

        // ✅ Initialize cache and device ID
        messageCache = MessageCache.getInstance();
        myId = DeviceUtil.getDeviceId(requireContext());

        // Setup RecyclerView
        chatAdapter = new ChatAdapter(myId, this);
        rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMessages.setAdapter(chatAdapter);

        // ✅ INSTANT DISPLAY: Load from cache first
        loadFromCache();

        // ✅ Then load full history from database
        loadChatHistory();

        // ✅ Always show chat header (Global or Private)
        if (chatHeader != null) {
            chatHeader.setVisibility(View.VISIBLE);
            updateChatHeader();

            // Bind Close Button
            View btnClose = chatHeader.findViewById(R.id.btnClosePrivateChat);
            if (btnClose != null) {
                btnClose.setOnClickListener(v -> switchToGlobalChat());
            }
        }

        if (recipientName != null) {
            etMessage.setHint("Message " + recipientName + "...");
        } else {
            etMessage.setHint("Broadcast to everyone...");
        }

        // Send button click
        btnSend.setOnClickListener(v -> sendMessage());

        // Location button click
        ImageButton btnLocation = view.findViewById(R.id.btnLocation);
        if (btnLocation != null) {
            btnLocation.setOnClickListener(v -> showLocationDurationDialog());
        }

        // SOS Button click (New)
        ImageButton btnSos = view.findViewById(R.id.btnSos);
        if (btnSos != null) {
            btnSos.setOnClickListener(v -> sendGlobalSos());
        }

        // Private Chats List (New)
        if (rvPrivateChats != null) {
            rvPrivateChats.setLayoutManager(new LinearLayoutManager(requireContext()));
            
            // Get connected members list
            java.util.List<com.example.disastercomm.models.MemberItem> peerList = new java.util.ArrayList<>();
            if (getActivity() instanceof com.example.disastercomm.MainActivityNew) {
                peerList.addAll(((com.example.disastercomm.MainActivityNew) getActivity()).getConnectedMembers().values());
            }

            privateChatsAdapter = new com.example.disastercomm.adapters.MembersAdapter(peerList);
            privateChatsAdapter.setOnMemberClickListener(member -> {
                // On Member Click -> Open Private Chat
                if (getActivity() instanceof com.example.disastercomm.MainActivityNew) {
                    ((com.example.disastercomm.MainActivityNew) getActivity()).openPrivateChat(member);
                }
            });
            rvPrivateChats.setAdapter(privateChatsAdapter);
        }
    }

    public void updatePrivateChatsList() {
        if (privateChatsAdapter != null && getActivity() instanceof com.example.disastercomm.MainActivityNew) {
            java.util.List<com.example.disastercomm.models.MemberItem> peerList = new java.util.ArrayList<>();
            peerList.addAll(((com.example.disastercomm.MainActivityNew) getActivity()).getConnectedMembers().values());
            privateChatsAdapter.updateMembers(peerList);
        }
    }

    private void showLocationDurationDialog() {
        if (!isAdded())
            return;

        final CharSequence[] options = { "15 Minutes", "30 Minutes", "1 Hour", "Continuous" };
        final long[] durations = {
                com.example.disastercomm.utils.LiveLocationSharingManager.DURATION_15_MIN,
                com.example.disastercomm.utils.LiveLocationSharingManager.DURATION_30_MIN,
                com.example.disastercomm.utils.LiveLocationSharingManager.DURATION_1_HOUR,
                com.example.disastercomm.utils.LiveLocationSharingManager.DURATION_CONTINUOUS
        };

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Share Live Location")
                .setItems(options, (dialog, which) -> {
                    long duration = durations[which];
                    startLocationSharing(duration);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startLocationSharing(long duration) {
        if (!isAdded())
            return;

        if (androidx.core.app.ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.widget.Toast
                    .makeText(requireContext(), "Location permission required", android.widget.Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        // 1. Start Service
        com.example.disastercomm.services.LiveLocationService.startSharing(requireContext(), duration);

        // 2. Send initial location message
        com.example.disastercomm.utils.LocationHelper locationHelper = new com.example.disastercomm.utils.LocationHelper(
                requireContext());
        locationHelper.getCurrentLocation((lat, lng) -> {
            if (packetHandler != null) {
                String content = lat + "," + lng;
                Message locMsg = new Message(
                        DeviceUtil.getDeviceId(requireContext()),
                        username,
                        Message.Type.LOCATION_UPDATE,
                        content);

                locMsg.isLiveSharing = true;

                // CRITICAL: Set correct recipient
                if (recipientId != null) {
                    locMsg.receiverId = recipientId;
                } else {
                    locMsg.receiverId = "ALL";
                }

                packetHandler.sendMessage(locMsg);

                // Show in UI
                requireActivity().runOnUiThread(() -> {
                    addMessage(locMsg);
                    android.widget.Toast
                            .makeText(requireContext(), "📍 Sharing Live Location", android.widget.Toast.LENGTH_SHORT)
                            .show();
                });
            }
        });
    }

    private void sendGlobalSos() {
        if (!isAdded()) return;

        if (androidx.core.app.ActivityCompat.checkSelfPermission(requireContext(),
                android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            android.widget.Toast.makeText(requireContext(), "Location permission required for SOS", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        android.widget.Toast.makeText(requireContext(), "🚨 Fetching location for SOS...", android.widget.Toast.LENGTH_SHORT).show();

        com.example.disastercomm.utils.LocationHelper locationHelper = new com.example.disastercomm.utils.LocationHelper(requireContext());
        locationHelper.getCurrentLocation((lat, lng) -> {
            if (packetHandler != null) {
                String content = "🚨 EMERGENCY SOS! Location: " + lat + ", " + lng;
                Message sosMsg = new Message(
                        DeviceUtil.getDeviceId(requireContext()),
                        username,
                        Message.Type.SOS,
                        content);

                // CRITICAL: Always broadcast SOS to ALL, ignoring private chat status
                sosMsg.receiverId = "ALL";
                sosMsg.priority = 10;

                packetHandler.sendMessage(sosMsg);

                // Show in UI
                requireActivity().runOnUiThread(() -> {
                    addMessage(sosMsg);
                    android.widget.Toast.makeText(requireContext(), "✅ Global SOS Sent!", android.widget.Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }

        if (packetHandler != null) {
            Message msg = new Message(
                    DeviceUtil.getDeviceId(requireContext()),
                    username,
                    Message.Type.TEXT,
                    text);

            // ✅ Set recipient - CRITICAL for member chat
            if (recipientId != null) {
                msg.receiverId = recipientId; // Send to specific member
                MessageDebugHelper.logMessageSent(msg.id, recipientId, text);
                android.util.Log.d("ChatFragment",
                        "✅ Sending to MEMBER: " + recipientId + " (" + recipientName + ") - Message: " + text);
            } else {
                msg.receiverId = "ALL"; // Broadcast to everyone
                MessageDebugHelper.logMessageSent(msg.id, "ALL", text);
                android.util.Log.d("ChatFragment", "✅ Sending BROADCAST - Message: " + text);
            }

            msg.status = Message.Status.SENT;

            // Send via network
            packetHandler.sendMessage(msg);

            // Add to UI immediately
            chatAdapter.addMessage(msg);
            rvMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);

            // Clear input
            etMessage.setText("");
        }
    }

    public void addMessage(Message message) {
        // ✅ CRASH PREVENTION: Check if fragment is attached
        if (!isAdded() || getActivity() == null) {
            android.util.Log.w("ChatFragment", "Fragment not attached, skipping addMessage");
            return;
        }

        if (chatAdapter != null) {
            chatAdapter.addMessage(message);
            if (rvMessages != null) {
                rvMessages.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
            }
        }

        // ✅ Update cache
        if (messageCache != null && myId != null) {
            messageCache.addMessage(myId, recipientId, message);
        }
    }

    public void processReceivedMessage(Message message) {
        if (!isAdded())
            return;

        requireActivity().runOnUiThread(() -> {
            // 1. Handle Receipts (Visual Confirmation)
            if (message.type == Message.Type.DELIVERY_RECEIPT || message.type == Message.Type.READ_RECEIPT) {
                if (chatAdapter != null && message.receiptFor != null) {
                    Message.Status status = (message.type == Message.Type.READ_RECEIPT) ? Message.Status.READ
                            : Message.Status.DELIVERED;

                    chatAdapter.updateMessageStatus(message.receiptFor, status);
                    android.util.Log.d("ChatFragment",
                            "✅ Updated status for msg " + message.receiptFor + " to " + status);
                }
                return; // Don't show receipts as text bubbles
            }

            // 2. Handle Normal Messages
            boolean isGlobalMsg = "ALL".equals(message.receiverId);
            boolean isGlobalView = (recipientId == null);
            
            boolean isForCurrentRecipient = false;
            if (!isGlobalMsg && recipientId != null) {
                isForCurrentRecipient = message.senderId.equals(recipientId) || message.receiverId.equals(recipientId);
            }
            
            if (isGlobalView && isGlobalMsg) {
                addMessage(message); // Render in Global
            } else if (!isGlobalView && isForCurrentRecipient) {
                addMessage(message); // Render in Private Chat
            } else {
                // Background message - DO NOT render, but add to cache
                if (messageCache != null && myId != null) {
                    if (isGlobalMsg) {
                        messageCache.addMessage(myId, null, message);
                    } else {
                        String otherUserId = message.senderId.equals(myId) ? message.receiverId : message.senderId;
                        messageCache.addMessage(myId, otherUserId, message);
                    }
                }
                android.util.Log.d("ChatFragment", "Silently cached background message from " + message.senderId);
            }
        });
    }

    public ChatAdapter getChatAdapter() {
        return chatAdapter;
    }

    /**
     * ✅ INSTANT DISPLAY: Load messages from cache first (<50ms)
     */
    private void loadFromCache() {
        if (!isAdded() || messageCache == null || myId == null)
            return;

        List<Message> cachedMessages = messageCache.getMessages(myId, recipientId);
        if (!cachedMessages.isEmpty()) {
            android.util.Log.d("ChatFragment", "Loading " + cachedMessages.size() + " messages from cache");
            for (Message m : cachedMessages) {
                chatAdapter.addMessage(m);
            }
            if (chatAdapter.getItemCount() > 0 && rvMessages != null) {
                rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
            }
        }
    }

    private void loadChatHistory() {
        if (!isAdded())
            return; // Safety check

        com.example.disastercomm.data.AppDatabase db = com.example.disastercomm.data.AppDatabase
                .getDatabase(requireContext());
        com.example.disastercomm.data.AppDatabase.databaseWriteExecutor.execute(() -> {
            java.util.List<Message> history;
            String myId = DeviceUtil.getDeviceId(requireContext());

            if (recipientId != null) {
                // ✅ PAGINATION: Load recent 100 messages for private chat
                android.util.Log.d("ChatFragment", "🔍 LOADING PRIVATE messages with: " + recipientId);
                android.util.Log.d("ChatFragment", "   My ID: " + myId);
                history = db.messageDao().getRecentPrivateMessages(myId, recipientId, 100);
                android.util.Log.d("ChatFragment", "📊 Found " + history.size() + " private messages in DB");
            } else {
                // ✅ PAGINATION: Load recent 100 global messages
                android.util.Log.d("ChatFragment", "🔍 LOADING GLOBAL messages");
                java.util.List<Message> global = db.messageDao().getRecentGlobalMessages(100);
                java.util.List<Message> sos = db.messageDao().getRecentSosMessages(50);
                android.util.Log.d("ChatFragment",
                        "📊 Found " + global.size() + " global + " + sos.size() + " SOS messages");
                history = new java.util.ArrayList<>(global);
                history.addAll(sos);
                // Sort by timestamp
                java.util.Collections.sort(history, (m1, m2) -> Long.compare(m1.timestamp, m2.timestamp));
            }

            if (history != null && !history.isEmpty()) {
                // ✅ CRASH PREVENTION: Check if fragment still attached
                if (getActivity() == null || !isAdded())
                    return;

                requireActivity().runOnUiThread(() -> {
                    // ✅ Double-check on UI thread
                    if (!isAdded() || chatAdapter == null)
                        return;

                    // Clear adapter before adding (cache already displayed)
                    chatAdapter.clearMessages();

                    for (Message m : history) {
                        chatAdapter.addMessage(m);
                        // ✅ Populate cache for next time
                        if (messageCache != null && myId != null) {
                            messageCache.addMessage(myId, recipientId, m);
                        }
                    }
                    if (chatAdapter.getItemCount() > 0 && rvMessages != null) {
                        rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
                    }

                    // ✅ MARK READ: Send Read Receipt for unread messages from this recipient
                    if (recipientId != null && packetHandler != null) {
                        com.example.disastercomm.data.AppDatabase.databaseWriteExecutor.execute(() -> {
                            for (Message m : history) {
                                if (!m.isRead && m.senderId.equals(recipientId) && m.status != Message.Status.READ) {
                                    Message readReceipt = Message.createReadReceipt(m.id, myId, username);
                                    readReceipt.receiverId = recipientId;
                                    packetHandler.sendMessage(readReceipt);

                                    // Update local DB
                                    m.isRead = true;
                                    db.messageDao().updateMessage(m);
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    /**
     * ✅ Add a local system message (not saved to DB, just for display)
     */
    public void addSystemMessage(String text) {
        if (!isAdded())
            return;

        // Create a fake message object for display
        Message sysMsg = new Message();
        sysMsg.id = "SYS_" + System.currentTimeMillis();
        sysMsg.senderId = "SYSTEM";
        sysMsg.senderName = "System";
        sysMsg.content = text;
        sysMsg.timestamp = System.currentTimeMillis();
        sysMsg.type = Message.Type.TEXT;

        // Ensure UI update on main thread
        requireActivity().runOnUiThread(() -> {
            if (chatAdapter != null) {
                chatAdapter.addMessage(sysMsg);
                if (rvMessages != null) {
                    rvMessages.scrollToPosition(chatAdapter.getItemCount() - 1);
                }
            }
        });
    }

    /**
     * ✅ Switch back to Global Chat
     */
    private void switchToGlobalChat() {
        if (recipientId == null)
            return; // Already global

        android.util.Log.d("ChatFragment", "🔄 Switching to GLOBAL CHAT");
        android.util.Log.d("ChatFragment", "🔄 Switching to GLOBAL CHAT");
        recipientId = null;
        recipientName = null;
        currentRecipient = null; // Clear rich object

        etMessage.setHint("Broadcast to everyone...");
        updateChatHeader();
        loadChatHistory(); // Reload global history

        android.widget.Toast
                .makeText(requireContext(), "📢 Switched to Global Broadcast", android.widget.Toast.LENGTH_SHORT)
                .show();
    }

    /**
     * ✅ Update chat header with member info or Global Broadcast info
     */
    private void updateChatHeader() {
        if (chatHeader == null || !isAdded())
            return;

        TextView tvChatMemberName = chatHeader.findViewById(R.id.tvChatMemberName);
        TextView tvChatOnlineStatus = chatHeader.findViewById(R.id.tvChatOnlineStatus);
        View viewChatOnlineStatus = chatHeader.findViewById(R.id.viewChatOnlineStatus);
        View btnClose = chatHeader.findViewById(R.id.btnClosePrivateChat);
        android.widget.ImageView ivAvatar = chatHeader.findViewById(R.id.ivChatAvatar);
        View ivChatSecure = chatHeader.findViewById(R.id.ivChatSecure);

        if (recipientId == null) {
            // 🌍 GLOBAL BROADCAST MODE
            if (tvChatMemberName != null)
                tvChatMemberName.setText("Global Broadcast Channel");
            if (tvChatOnlineStatus != null)
                tvChatOnlineStatus.setText("All Members");
            if (viewChatOnlineStatus != null)
                viewChatOnlineStatus.setVisibility(View.GONE); // No single status
            if (btnClose != null)
                btnClose.setVisibility(View.GONE); // Cant close global
            if (ivAvatar != null)
                ivAvatar.setImageResource(R.drawable.ic_app_logo); // Use app icon or generic
            if (ivChatSecure != null)
                ivChatSecure.setVisibility(View.GONE); // No E2EE on broadcast
        } else {
            // 👤 PRIVATE CHAT MODE
            if (tvChatMemberName != null)
                tvChatMemberName.setText(recipientName);

            // Toggle E2EE Secure Icon
            if (ivChatSecure != null) {
                if (packetHandler != null && packetHandler.hasPublicKey(recipientId)) {
                    ivChatSecure.setVisibility(View.VISIBLE);
                } else {
                    ivChatSecure.setVisibility(View.GONE);
                }
            }

            // ✅ RICH STATUS DISPLAY
            if (currentRecipient != null) {
                // Online/Offline status
                if (tvChatOnlineStatus != null) {
                    if (currentRecipient.isOnline) {
                        tvChatOnlineStatus.setText("Active now");
                        tvChatOnlineStatus.setTextColor(getResources().getColor(R.color.status_connected, null));
                        if (viewChatOnlineStatus != null)
                            viewChatOnlineStatus.setVisibility(View.VISIBLE);
                    } else {
                        tvChatOnlineStatus.setText(currentRecipient.getLastSeenText());
                        tvChatOnlineStatus.setTextColor(getResources().getColor(R.color.text_secondary, null));
                        if (viewChatOnlineStatus != null)
                            viewChatOnlineStatus.setVisibility(View.GONE);
                    }
                }

                // Connection Info (Source & Signal)
                TextView tvConnectionIcon = chatHeader.findViewById(R.id.tvChatConnectionIcon);
                TextView tvConnectionType = chatHeader.findViewById(R.id.tvChatConnectionType);
                TextView tvConnectionQuality = chatHeader.findViewById(R.id.tvChatConnectionQuality);

                if (tvConnectionIcon != null)
                    tvConnectionIcon.setText(currentRecipient.getConnectionIcon());
                if (tvConnectionType != null)
                    tvConnectionType.setText(currentRecipient.connectionSource);

                if (tvConnectionQuality != null) {
                    tvConnectionQuality.setText("Signal: " + currentRecipient.signalStrength);
                    try {
                        tvConnectionQuality.setTextColor(
                                android.graphics.Color.parseColor(currentRecipient.getSignalBadgeColor()));
                    } catch (Exception e) {
                    }
                }

            } else {
                // Fallback if we only have name/ID
                if (tvChatOnlineStatus != null)
                    tvChatOnlineStatus.setText("Offline");
            }

            if (btnClose != null)
                btnClose.setVisibility(View.VISIBLE);
            if (ivAvatar != null)
                ivAvatar.setImageResource(R.drawable.ic_members); // Use member icon
        }
    }

    @Override
    public void onLocationClick(String userId) {
        if (getActivity() instanceof com.example.disastercomm.MainActivityNew) {
            ((com.example.disastercomm.MainActivityNew) getActivity()).openMapAndTrackUser(userId);
        } else {
            android.widget.Toast.makeText(getContext(), "Debugging: Location clicked for user " + userId,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
