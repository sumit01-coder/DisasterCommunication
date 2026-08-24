package com.example.disastercomm.adapters;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.disastercomm.fragments.ChatFragment;
import com.example.disastercomm.fragments.NetworkDashboardFragment;
import com.example.disastercomm.fragments.RescueDashboardFragment;
import com.example.disastercomm.fragments.SOSFragment;
import com.example.disastercomm.network.PacketHandler;

/**
 * ViewPager2 adapter providing 4 pages:
 *  0 – Chat
 *  1 – SOS (Smart SOS Builder)
 *  2 – Network Intelligence Dashboard
 *  3 – Rescue Dashboard
 */
public class ViewPagerAdapter extends FragmentStateAdapter {

    private final ChatFragment chatFragment;
    private final SOSFragment sosFragment;
    private final NetworkDashboardFragment networkDashboardFragment;
    private final RescueDashboardFragment rescueDashboardFragment;

    // Keep old MapFragment ref for backwards compat (openMapAndTrackUser)
    private final com.example.disastercomm.fragments.MapFragment mapFragment;

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity,
            PacketHandler packetHandler,
            String username) {
        super(fragmentActivity);

        this.mapFragment = new com.example.disastercomm.fragments.MapFragment();
        this.chatFragment = ChatFragment.newInstance(packetHandler, username);
        this.sosFragment = SOSFragment.newInstance();
        this.networkDashboardFragment = NetworkDashboardFragment.newInstance();
        this.rescueDashboardFragment = RescueDashboardFragment.newInstance();
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        android.util.Log.d("ViewPagerAdapter", "📱 createFragment called for position: " + position);
        switch (position) {
            case 0: return mapFragment;
            case 1: return chatFragment;
            case 2: return sosFragment;
            case 3: return networkDashboardFragment;
            case 4: return rescueDashboardFragment;
            default: return mapFragment;
        }
    }

    @Override
    public int getItemCount() {
        return 5; // Map, Chat, SOS, Network, Rescue
    }

    // ── Getters (used by MainActivityNew) ──────────────────────────────────────

    public ChatFragment getChatFragment() { return chatFragment; }
    public SOSFragment getSOSFragment() { return sosFragment; }
    public NetworkDashboardFragment getNetworkDashboardFragment() { return networkDashboardFragment; }
    public RescueDashboardFragment getRescueDashboardFragment() { return rescueDashboardFragment; }

    /** Kept for backwards-compat with openMapAndTrackUser() */
    public com.example.disastercomm.fragments.MapFragment getMapFragment() { return mapFragment; }
    /** Kept for backwards-compat with updateMembersFragment() */
    public com.example.disastercomm.fragments.MembersFragment getMembersFragment() { return null; }
}
