package com.example.disastercomm.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.disastercomm.fragments.ChatFragment;
import com.example.disastercomm.fragments.MapFragment;
import com.example.disastercomm.fragments.MembersFragment;
import com.example.disastercomm.network.PacketHandler;

public class ViewPagerAdapter extends FragmentStateAdapter {

    private final MapFragment mapFragment;
    private final ChatFragment chatFragment;
    private final MembersFragment membersFragment;

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity,
            PacketHandler packetHandler,
            String username) {
        super(fragmentActivity);

        this.mapFragment = new MapFragment();
        this.chatFragment = ChatFragment.newInstance(packetHandler, username);
        this.membersFragment = new MembersFragment();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        android.util.Log.d("ViewPagerAdapter", "📱 createFragment called for position: " + position);
        switch (position) {
            case 0:
                android.util.Log.d("ViewPagerAdapter", "   → Returning MapFragment");
                return mapFragment;
            case 1:
                android.util.Log.d("ViewPagerAdapter", "   → Returning ChatFragment");
                return chatFragment;
            case 2:
                android.util.Log.d("ViewPagerAdapter", "   → Returning MembersFragment");
                return membersFragment;
            default:
                android.util.Log.d("ViewPagerAdapter", "   → Default: Returning MapFragment");
                return mapFragment;
        }
    }

    @Override
    public int getItemCount() {
        return 3; // Map, Chat, Members
    }

    public MapFragment getMapFragment() {
        return mapFragment;
    }

    public ChatFragment getChatFragment() {
        return chatFragment;
    }

    public MembersFragment getMembersFragment() {
        return membersFragment;
    }
}
