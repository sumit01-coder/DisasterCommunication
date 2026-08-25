package com.example.disastercomm;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.disastercomm.adapters.ViewPagerAdapter;
import com.example.disastercomm.data.AppDatabase;
import com.example.disastercomm.fragments.ChatFragment;
import com.example.disastercomm.fragments.MapFragment;
import com.example.disastercomm.fragments.MembersFragment;
import com.example.disastercomm.models.MemberItem;
import com.example.disastercomm.models.Message;
import com.example.disastercomm.network.MeshNetworkManager;
import com.example.disastercomm.network.NetworkStateMonitor;
import com.example.disastercomm.network.PacketHandler;
import com.example.disastercomm.utils.DeviceUtil;
import com.example.disastercomm.utils.LocationHelper;
import com.example.disastercomm.utils.NotificationSoundManager;
import com.example.disastercomm.utils.MessageDebugHelper; // ✅ Debug helper
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivityNew extends AppCompatActivity implements
        MeshNetworkManager.MeshCallback,
        PacketHandler.MessageListener {

    private androidx.drawerlayout.widget.DrawerLayout drawerLayout;
    private com.google.android.material.navigation.NavigationView navigationView;
    private androidx.appcompat.widget.Toolbar toolbar;
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private ExtendedFloatingActionButton fabSos;
    private TextView tvConnectionStatus;
    private View viewStatusDot;

    private ViewPagerAdapter pagerAdapter;
    private MeshNetworkManager meshNetworkManager;
    private PacketHandler packetHandler;
    
    // MeshRoutingTable instance to populate Dashboard Network Intelligence
    private com.example.disastercomm.network.MeshRoutingTable routingTable = new com.example.disastercomm.network.MeshRoutingTable();

    public PacketHandler getPacketHandler() {
        return packetHandler;
    }
    private com.example.disastercomm.network.BluetoothConnectionManager bluetoothConnectionManager;
    private NetworkStateMonitor networkStateMonitor;
    private NotificationSoundManager notificationSoundManager;
    private LocationHelper locationHelper;

    // ✅ Service Binding
    private com.example.disastercomm.services.NetworkService mService;
    private boolean mBound = false;
    private final android.content.ServiceConnection connection = new android.content.ServiceConnection() {
        @Override
        public void onServiceConnected(android.content.ComponentName className, android.os.IBinder service) {
            com.example.disastercomm.services.NetworkService.LocalBinder binder = (com.example.disastercomm.services.NetworkService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            Log.d("DisasterApp", "✅ Connected to NetworkService");

            // Retrieve persistent managers
            meshNetworkManager = mService.getMeshManager();
            bluetoothConnectionManager = mService.getBluetoothManager();
            packetHandler = mService.getPacketHandler();

            // Set listeners
            if (packetHandler != null) {
                packetHandler.setMessageListener(MainActivityNew.this);
                // Also broadcast public key now that we are connected
                packetHandler.broadcastPublicKey(username);
            }

            // ✅ RESTORE STATE: Sync connected devices from Service to UI
            if (meshNetworkManager != null) {
                for (String endpointId : meshNetworkManager.getConnectedEndpoints()) {
                    String name = meshNetworkManager.getConnectedDeviceName(endpointId);
                    addMember(endpointId, name != null ? name : "Unknown", "Mesh");
                }
            }
            if (bluetoothConnectionManager != null) {
                java.util.Map<String, String> btDevices = bluetoothConnectionManager.getConnectedDevices();
                for (java.util.Map.Entry<String, String> entry : btDevices.entrySet()) {
                    addMember(entry.getKey(), entry.getValue(), "Bluetooth");
                }
            }

            // Initialize UI components that depend on managers
            setupViewPagerAdapter();

            // Retry offline messages
            if (packetHandler != null)
                packetHandler.retryOfflineMessages();
        }

        @Override
        public void onServiceDisconnected(android.content.ComponentName arg0) {
            mBound = false;
            mService = null;
            Log.d("DisasterApp", "❌ Disconnected from NetworkService");
        }
    };

    // ✅ Broadcast Receiver for Service Updates
    private final android.content.BroadcastReceiver networkReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (intent == null || intent.getAction() == null)
                return;
            if ("com.example.disastercomm.NETWORK_UPDATE".equals(intent.getAction())) {
                String action = intent.getStringExtra("action");
                String id = intent.getStringExtra("id");
                String extra = intent.getStringExtra("extra"); // Name

                if ("MESH_CONNECTED".equals(action)) {
                    onDeviceConnected(id, extra);
                } else if ("MESH_DISCONNECTED".equals(action)) {
                    onDeviceDisconnected(id);
                } else if ("BT_CONNECTED".equals(action)) {
                    if (bluetoothDeviceMap != null)
                        bluetoothDeviceMap.put(id, extra);
                    addMember(id, extra, "Bluetooth");
                    Toast.makeText(context, "BT Connected: " + extra, Toast.LENGTH_SHORT).show();
                } else if ("BT_DISCONNECTED".equals(action)) {
                    if (bluetoothDeviceMap != null)
                        bluetoothDeviceMap.remove(id);
                    removeMember(id);
                }
            }
        }
    };

    private String username;
    private final Map<String, String> bluetoothDeviceMap = new HashMap<>();
    private final Map<String, MemberItem> connectedMembers = new HashMap<>();
    private ChatFragment privateChatFragment; // For direct messages
    private com.example.disastercomm.fragments.SOSFragment sosFragment;
    private com.example.disastercomm.fragments.NetworkDashboardFragment networkDashboardFragment;
    private com.example.disastercomm.fragments.RescueDashboardFragment rescueDashboardFragment;
    private com.example.disastercomm.utils.NotificationHelper notificationHelper;
    private com.example.disastercomm.utils.MessageCounter messageCounter; // ✅ Track unread messages
    private com.example.disastercomm.utils.ConnectivityStatusManager connectivityStatusManager;

    public Map<String, MemberItem> getConnectedMembers() {
        return connectedMembers;
    }

    private View viewUpdateBadge;

    private static final int PERMISSION_REQUEST_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("DisasterApp", "═══════════════════════════════════════════════════════");
        Log.d("DisasterApp", "🚀 MAIN ACTIVITY ONCREATE STARTED");
        Log.d("DisasterApp", "═══════════════════════════════════════════════════════");
        setContentView(R.layout.activity_main_new);

        // ✅ Auto-hide bottom nav when soft keyboard is visible
        final View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                android.graphics.Rect r = new android.graphics.Rect();
                rootView.getWindowVisibleDisplayFrame(r);
                int screenHeight = rootView.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;

                if (bottomNav != null) {
                    bottomNav.setVisibility(keypadHeight > screenHeight * 0.15 ? View.GONE : View.VISIBLE);
                }
            });
        }

        // ✅ Three-tier username persistence to ensure it NEVER changes:
        // 1. Saved instance state (activity recreation)
        // 2. SharedPreferences (app restart)
        // 3. Intent or generated (first launch)

        com.example.disastercomm.utils.PreferenceManager prefs = new com.example.disastercomm.utils.PreferenceManager(
                this);

        if (savedInstanceState != null && savedInstanceState.containsKey("username")) {
            // Tier 1: Restore from saved instance state (highest priority)
            username = savedInstanceState.getString("username");
            Log.d("DisasterApp", "✅ Username restored from saved state: " + username);
        } else {
            // Tier 2: Try SharedPreferences (for app restarts)
            String savedUsername = prefs.getUsername(null);
            if (savedUsername != null && !savedUsername.isEmpty()) {
                username = savedUsername;
                Log.d("DisasterApp", "✅ Username restored from SharedPreferences: " + username);
            } else {
                // Tier 3: Try intent, then generate
                username = getIntent().getStringExtra("username");
                if (username == null) {
                    username = "User" + DeviceUtil.getDeviceId(this).substring(0, 4);
                    Log.d("DisasterApp", "✅ Username generated: " + username);
                } else {
                    Log.d("DisasterApp", "✅ Username from intent: " + username);
                }
                // Save to SharedPreferences for next time
                prefs.setUsername(username);
                Log.d("DisasterApp", "💾 Username saved to SharedPreferences");
            }
        }

        Log.d("DisasterApp", "👤 Username: " + username);
        Log.d("DisasterApp", "📱 Device ID: " + DeviceUtil.getDeviceId(this));

        initViews();
        initViews();
        // Managers are now initialized via Service Binding
        // initManagers(); -> Moved to onServiceConnected

        setupBottomNavigation();
        // setupSosButton(); (Removed as part of Phase 1 theme update)

        checkAndRequestPermissions();

        // Handle notification clicks
        handleIntent(getIntent());

        // Auto-check for updates and show badge if available
        checkForUpdatesWithBadge();

        Log.d("DisasterApp", "✅ MAIN ACTIVITY ONCREATE COMPLETED");
    }

    private void checkAndRequestPermissions() {
        if (!hasPermissions()) {
            requestPermissions();
        } else {
            startNetworkServices();
        }
    }

    private boolean hasPermissions() {
        for (String permission : getRequiredPermissions()) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this,
                    permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || !intent.hasExtra("sender_id")) {
            return;
        }

        String senderId = intent.getStringExtra("sender_id");
        Log.d("DisasterApp", "🔔 Notification clicked for Sender ID: " + senderId);

        // 1. Check active connections
        if (connectedMembers.containsKey(senderId)) {
            openPrivateChat(connectedMembers.get(senderId));
            return;
        }

        // 2. Check persistent users (Database)
        AppDatabase.databaseWriteExecutor.execute(() -> {
            com.example.disastercomm.models.User user = AppDatabase.getDatabase(this).userDao().getUser(senderId);
            runOnUiThread(() -> {
                String name = (user != null) ? user.name : "Member " + senderId.substring(0, 4);
                MemberItem item = new MemberItem(senderId, name);
                openPrivateChat(item);
            });
        });
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSION_REQUEST_CODE);
    }

    private String[] getRequiredPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return new String[] { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.POST_NOTIFICATIONS };
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return new String[] { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION };
        } else {
            return new String[] { Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION };
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && allPermissionsGranted(grantResults)) {
                startNetworkServices();
            } else {
                Toast.makeText(this, "Permissions required for connectivity", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean allPermissionsGranted(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED)
                return false;
        }
        return true;
    }

    private void startNetworkServices() {
        Log.d("DisasterApp", "Starting Network Services...");

        // Start and Bind Service
        Intent intent = new Intent(this, com.example.disastercomm.services.NetworkService.class);
        intent.putExtra("username", username);
        startService(intent); // Start foreground service
        bindService(intent, connection, android.content.Context.BIND_AUTO_CREATE);

        // Register Broadcast Receiver
        android.content.IntentFilter filter = new android.content.IntentFilter(
                "com.example.disastercomm.NETWORK_UPDATE");
        androidx.core.content.ContextCompat.registerReceiver(
                this, networkReceiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);

        // Initialize other local helpers
        notificationSoundManager = new NotificationSoundManager(this);
        notificationHelper = new com.example.disastercomm.utils.NotificationHelper(this);
        messageCounter = com.example.disastercomm.utils.MessageCounter.getInstance(this);

        // Network Monitor
        networkStateMonitor = new NetworkStateMonitor(this, new NetworkStateMonitor.NetworkStateListener() {
            @Override
            public void onNetworkAvailable(int networkType, String networkName) {
                runOnUiThread(() -> updateConnectionStatus(true));
            }

            @Override
            public void onNetworkLost(int networkType) {
                runOnUiThread(() -> updateConnectionStatus(connectedMembers.size() > 0));
            }

            @Override
            public void onInternetAvailable() {
            }

            @Override
            public void onInternetLost() {
            }
        });
        networkStateMonitor.startMonitoring();

        // Location Helper
        locationHelper = new LocationHelper(this);

        // Show system notification
        if (notificationHelper != null) {
            notificationHelper.showSystemNotification("DisasterComm Started", "Network services are active");
        }
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        toolbar = findViewById(R.id.toolbar);
        viewPager = findViewById(R.id.viewPager);
        bottomNav = findViewById(R.id.bottomNav);

        // Status indicators
        viewUpdateBadge = findViewById(R.id.viewUpdateBadge);

        // Setup ViewPager
        viewPager.setUserInputEnabled(true); // Allow swipe

        // Setup Drawer
        setupNavigationDrawer();

        // Setup connectivity status monitoring
        setupConnectivityStatus();

        // Setup update notification
        setupUpdateNotification();
    }

    private void setupNavigationDrawer() {
        // Use standard ActionBarDrawerToggle linked to Toolbar for smooth animation
        androidx.appcompat.app.ActionBarDrawerToggle toggle = new androidx.appcompat.app.ActionBarDrawerToggle(this,
                drawerLayout, toolbar, // Pass
                                       // toolbar
                                       // to
                                       // handle
                                       // icon
                                       // and
                                       // animation
                                       // automatically
                R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                // Hide keyboard when drawer opens
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(
                        INPUT_METHOD_SERVICE);
                if (imm != null && getCurrentFocus() != null) {
                    imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
                }
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
            }
        };

        // Set white color for the hamburger icon
        toggle.getDrawerArrowDrawable().setColor(getResources().getColor(R.color.white, null));

        // Add drawer listener for animations
        drawerLayout.addDrawerListener(toggle);

        // Sync the toggle state
        toggle.syncState();

        // Enable swipe gesture
        drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED);

        // Enable swipe gesture
        drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED);

        // Update nav header
        View headerView = navigationView.getHeaderView(0);
        TextView tvUserName = headerView.findViewById(R.id.tvUserName);
        TextView tvUserInitial = headerView.findViewById(R.id.tvUserInitial);
        TextView tvDeviceId = headerView.findViewById(R.id.tvDeviceId);
        TextView tvUserRole = headerView.findViewById(R.id.tvUserRole);
        
        tvUserName.setText(username);
        if (tvUserRole != null) {
            String role = getSharedPreferences("UserProfile", MODE_PRIVATE).getString("role", "Civilian");
            tvUserRole.setText(role);
        }
        
        String manufacturer = android.os.Build.MANUFACTURER;
        String model = android.os.Build.MODEL;
        String deviceName = model;
        if (manufacturer != null && !model.toLowerCase().startsWith(manufacturer.toLowerCase())) {
            // Capitalize manufacturer name
            manufacturer = manufacturer.substring(0, 1).toUpperCase() + manufacturer.substring(1);
            deviceName = manufacturer + " " + model;
        }
        
        String deviceId = DeviceUtil.getDeviceId(this);
        tvDeviceId.setText(deviceName + " • ID: " + deviceId.substring(0, Math.min(8, deviceId.length())));

        // Set avatar initial
        if (tvUserInitial != null && username != null && !username.isEmpty()) {
            tvUserInitial.setText(username.substring(0, 1).toUpperCase());
        }

        // Rescue Dashboard RBAC
        String userRole = getSharedPreferences("UserProfile", MODE_PRIVATE).getString("role", "Civilian");
        if (!"Rescue Team".equals(userRole)) {
            android.view.Menu navMenu = navigationView.getMenu();
            android.view.MenuItem rescueItem = navMenu.findItem(R.id.nav_rescue_dashboard);
            if (rescueItem != null) {
                rescueItem.setVisible(false);
            }
        }

        // Navigation menu clicks
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // Close drawer first for smooth UX
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);

            // Delay action slightly for smooth close animation
            new android.os.Handler().postDelayed(() -> {
                if (itemId == R.id.nav_network_status) {
                    showNetworkStatusDialog();
                } else if (itemId == R.id.nav_rescue_dashboard) {
                    viewPager.setCurrentItem(5, false); // Index 5 is Rescue Dashboard
                } else if (itemId == R.id.nav_bluetooth_devices) {
                    showBluetoothDevicesDialog();
                } else if (itemId == R.id.nav_nearby_devices) {
                    showNearbyDevicesDialog();
                } else if (itemId == R.id.nav_settings) {
                    startActivity(new android.content.Intent(this, SettingsActivity.class));
                } else if (itemId == R.id.nav_about) {
                    showAboutDialog();
                }
            }, 250); // 250ms
                     // delay
                     // for
                     // smooth
                     // drawer
                     // close

            return true;
        });
    }

    // ✅ Inflate Menu
    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    // ✅ Handle Scan Button Click
    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_scan_network) {
            triggerManualDeviceScan();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // Logic for manual scan
    public void triggerManualDeviceScan() {
        if (notificationSoundManager != null)
            notificationSoundManager.vibrate(50);
        android.widget.Toast.makeText(this, "🔄 Scanning for connection...", android.widget.Toast.LENGTH_SHORT).show();

        // 1. Mesh Scan (WiFi Direct)
        if (meshNetworkManager != null) {
            meshNetworkManager.startDiscovery();
        }

        // 2. Bluetooth Scan
        if (bluetoothConnectionManager != null) {
            bluetoothConnectionManager.startDiscovery();
        }

        // 3. Wi-Fi Aware (NAN) Scan
        if (mService != null && mService.getWifiAwareNetworkManager() != null) {
            // Re-trigger publish/subscribe or specific scan logic if needed
            // For now, restarting acts as a "fresh" scan
            mService.getWifiAwareNetworkManager().stop();
            mService.getWifiAwareNetworkManager().start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Update username if changed in settings
        com.example.disastercomm.utils.PreferenceManager prefs = new com.example.disastercomm.utils.PreferenceManager(
                this);
        String savedName = prefs.getUsername(username);
        if (!savedName.equals(username)) {
            username = savedName;
            View headerView = navigationView.getHeaderView(0);
            if (headerView != null) {
                TextView tvUserName = headerView.findViewById(R.id.tvUserName);
                TextView tvUserInitial = headerView.findViewById(R.id.tvUserInitial);
                if (tvUserName != null)
                    tvUserName.setText(username);
                if (tvUserInitial != null && username != null && !username.isEmpty())
                    tvUserInitial.setText(username.substring(0, 1).toUpperCase());
            }
        }

        // Update connectivity status
        if (connectivityStatusManager != null) {
            connectivityStatusManager.startMonitoring();
            updateConnectivityIcons();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d("DisasterApp", "⚙️ Configuration changed - maintaining connections");
        // Activity will not restart thanks to android:configChanges
        // Network connections remain intact
        // Just handle any UI adjustments if needed
    }

    @Override
    public void onBackPressed() {
        // Close private chat if open
        if (privateChatFragment != null && privateChatFragment.isVisible()) {
            getSupportFragmentManager().beginTransaction().remove(privateChatFragment).commit();
            findViewById(R.id.fragment_container).setVisibility(View.GONE);
            privateChatFragment = null;
            return;
        }

        // Close drawer if open, otherwise normal back behavior
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    /**
     * ✅ Handle notification tap - open specific member chat
     */
    private void handleNotificationIntent(Intent intent) {
        if (intent != null && intent.hasExtra("OPEN_CHAT_WITH_ID")) {
            String memberId = intent.getStringExtra("OPEN_CHAT_WITH_ID");
            String memberName = intent.getStringExtra("OPEN_CHAT_WITH_NAME");

            Log.d("DisasterApp", "📬 Notification tap - opening chat with: " + memberName);

            // Delay to ensure UI is ready
            new android.os.Handler().postDelayed(() -> {
                if (memberId != null && memberName != null) {
                    // Create MemberItem and open chat
                    MemberItem member = new MemberItem(memberId, memberName); // ✅ Fixed: ID first, then Name
                    openPrivateChat(member);
                }
            }, 500); // 500ms delay
        }
    }

    public void openMapAndTrackUser(String userId, String locationContent) {
        Log.d("DisasterApp", "📍 Switching to Map to track user: " + userId);

        // 1. Switch to Map Tab
        if (viewPager != null) {
            viewPager.setCurrentItem(0, true); // Index 0 = MapFragment
        }

        // 2. Focus on user
        if (pagerAdapter != null) {
            final com.example.disastercomm.fragments.MapFragment mapFragment = pagerAdapter.getMapFragment();
            if (mapFragment != null) {
                // Delay slightly to allow ViewPager to settle if needed, or just call directly
                new android.os.Handler().postDelayed(() -> mapFragment.focusOnUser(userId, locationContent), 500);
            }
        }
    }

    public void openPrivateChat(MemberItem member) {
        Log.d("DisasterApp", "📝 Opening private chat with: " + member.name + " (ID: " + member.id + ")");

        // 1. Switch to Chat tab
        if (viewPager != null) {
            viewPager.setCurrentItem(1, true); // Index 1 = ChatFragment
            Log.d("DisasterApp", "   → Switched to CHAT tab");
        }

        // 2. Get ChatFragment and set recipient
        if (pagerAdapter != null) {
            ChatFragment chatFrag = pagerAdapter.getChatFragment();
            if (chatFrag != null) {
                // ✅ Use new method to pass full member details
                chatFrag.setRecipient(member);
                Log.d("DisasterApp", "   → Set chat recipient: " + member.name);

                // 3. Clear unread count for this member
                if (messageCounter != null) {
                    int unreadCount = messageCounter.getCount(member.id);
                    messageCounter.reset(member.id);
                    Log.d("DisasterApp", "   → Cleared " + unreadCount + " unread messages");
                }

                // 4. Refresh members list to update UI
                updateMembersFragment();

                // 5. Show user feedback
                Toast.makeText(this, "💬 Chat with " + member.name, Toast.LENGTH_SHORT).show();
            } else {
                Log.e("DisasterApp", "⚠️ ChatFragment is null from adapter");
            }
        } else {
            Log.e("DisasterApp", "⚠️ PagerAdapter is null, cannot open chat");
        }
    }

    private void showNetworkStatusDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_network_status, null);
        TextView tvAvailableNetworks = dialogView.findViewById(R.id.tvAvailableNetworks);
        TextView tvInternetStatus = dialogView.findViewById(R.id.tvInternetConnection);
        com.google.android.material.card.MaterialCardView viewInternetStatus = dialogView.findViewById(R.id.statusIndicator);

        // Update network info
        String networks = "Wi-Fi Direct: Active\nBluetooth: Active";
        if (networkStateMonitor != null && networkStateMonitor.hasAnyConnectivity()) {
            networks += "\nInternet: Available";
        }
        
        // Check for ESP32 connection
        boolean isHardwareHubConnected = false;
        try {
            android.net.wifi.WifiManager wifiManager = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(android.content.Context.WIFI_SERVICE);
            if (wifiManager != null && wifiManager.isWifiEnabled()) {
                android.net.wifi.WifiInfo info = wifiManager.getConnectionInfo();
                if (info != null && info.getNetworkId() != -1 && info.getSSID() != null) {
                    String ssid = info.getSSID().toUpperCase().replace("\"", "");
                    if (ssid.contains("ESP32") || ssid.contains("XIAO") || ssid.contains("LORA") || ssid.contains("HELTEC") || ssid.contains("LILYGO")) {
                        isHardwareHubConnected = true;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        networks += "\nHardware Hub (ESP32): " + (isHardwareHubConnected ? "Connected" : "Not Found");
        
        tvAvailableNetworks.setText(networks);

        boolean hasInternet = networkStateMonitor != null && networkStateMonitor.isConnectedToInternet();
        tvInternetStatus.setText(hasInternet ? "Connected" : "Not Available");
        tvInternetStatus.setTextColor(android.graphics.Color.parseColor(hasInternet ? "#00BFA5" : "#FF1744"));
        viewInternetStatus.setCardBackgroundColor(android.graphics.Color.parseColor(hasInternet ? "#00BFA5" : "#FF1744"));

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
                
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showBluetoothDevicesDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_bluetooth_devices, null);
        TextView tvDeviceList = dialogView.findViewById(R.id.tvDeviceList);

        String message = "";
        if (bluetoothDeviceMap.isEmpty()) {
            message += "No devices found.\n";
            message += "\nMake sure Bluetooth is enabled and nearby devices are discoverable.";
        } else {
            for (Map.Entry<String, String> entry : bluetoothDeviceMap.entrySet()) {
                message += "• " + entry.getValue() + "\n";
                message += "   Address: " + entry.getKey() + "\n\n";
            }
        }
        tvDeviceList.setText(message);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
                
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showNearbyDevicesDialog() {
        // Build comprehensive device information
        StringBuilder message = new StringBuilder();
        int connectedCount = 0;
        int totalCount = connectedMembers.size();

        // Count connected devices
        for (MemberItem member : connectedMembers.values()) {
            if (member.isOnline) {
                connectedCount++;
            }
        }

        // Header with device count
        message.append("╔══════════════════════════════╗\n");
        message.append(String.format("   %d Device%s Found (%d Connected)\n",
                totalCount, totalCount != 1 ? "s" : "", connectedCount));
        message.append("╚══════════════════════════════╝\n\n");

        if (connectedMembers.isEmpty()) {
            // Enhanced empty state
            message.append("🔍 No devices discovered yet\n\n");
            message.append("💡 Tips:\n");
            message.append("• Ensure other devices are within ~100m\n");
            message.append("• Both devices should have the app open\n");
            message.append("• Check that Bluetooth/WiFi are enabled\n");
            message.append("• Try tapping 'Refresh' to scan again\n\n");
            message.append("⚙️ Discovery is running in background...");
        } else {
            // Show each device with rich information
            for (MemberItem member : connectedMembers.values()) {
                // Connection icon and name
                message.append(member.getConnectionIcon()).append(" ");
                message.append(member.name).append("\n");

                // Connection status with indicator
                String statusIcon = member.isOnline ? "🟢" : "🔴";
                String statusText = member.isOnline ? "Connected" : "Offline";
                message.append("   ").append(statusIcon).append(" ").append(statusText);
                message.append(" (").append(member.connectionSource).append(")\n");

                // Signal strength with visual indicator
                String signalIcon = getSignalStrengthIcon(member.signalStrength);
                message.append("   ").append(signalIcon).append(" Signal: ");
                message.append(member.signalStrength);
                if (member.connectionQuality > 0) {
                    message.append(" (").append(member.connectionQuality).append("%)");
                }
                message.append("\n");

                // Distance
                if (member.distance > 0) {
                    message.append("   📏 ").append(member.getDistanceText()).append("\n");
                }

                // Last seen/active
                message.append("   🕒 ").append(member.getLastSeenText()).append("\n");

                // Hop count for mesh
                if (member.hopCount > 1) {
                    message.append("   🔗 ").append(member.hopCount).append(" hop");
                    if (member.hopCount > 1)
                        message.append("s");
                    message.append(" away\n");
                }

                message.append("\n");
            }

            // Footer with last update time
            message.append("───────────────────────────────\n");
            message.append("Last updated: ").append(getCurrentTime());
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_nearby_devices, null);
        TextView tvDeviceList = dialogView.findViewById(R.id.tvDeviceList);
        tvDeviceList.setText(message.toString());

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
                
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        
        dialogView.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btnRefresh).setOnClickListener(v -> {
            triggerManualDeviceScan();
            dialog.dismiss();
            new android.os.Handler().postDelayed(this::showNearbyDevicesDialog, 1000);
        });

        dialog.show();
    }

    /**
     * Get signal strength icon based on signal quality
     */
    private String getSignalStrengthIcon(String signalStrength) {
        if (signalStrength == null)
            return "📶";

        switch (signalStrength.toLowerCase()) {
            case "strong":
                return "📶"; // Full bars
            case "medium":
                return "📶"; // Medium bars (could use different emoji)
            case "weak":
                return "📡"; // Weak signal
            default:
                return "📶";
        }
    }

    /**
     * Get current time formatted as HH:MM
     */
    private String getCurrentTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }

    private void showAboutDialog() {
        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_about, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(view)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        view.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void setupViewPagerAdapter() {
        if (pagerAdapter == null && packetHandler != null) {
            Log.d("DisasterApp", "📑 Creating ViewPagerAdapter");
            String userRole = getSharedPreferences("UserProfile", MODE_PRIVATE).getString("role", "Civilian");
            pagerAdapter = new ViewPagerAdapter(this, packetHandler, username, userRole);
            viewPager.setAdapter(pagerAdapter);
            viewPager.setOffscreenPageLimit(5);
            Log.d("DisasterApp", "✅ ViewPager setup complete");

            // Store fragment references for data passing
            sosFragment = pagerAdapter.getSOSFragment();
            networkDashboardFragment = pagerAdapter.getNetworkDashboardFragment();
            rescueDashboardFragment = pagerAdapter.getRescueDashboardFragment();

            // Restore MembersFragment scan callback
            if (pagerAdapter.getMembersFragment() != null) {
                pagerAdapter.getMembersFragment().setConnectionListener(() -> triggerManualDeviceScan());
            }
        }
    }

    // Deprecated: Logic moved to NetworkService
    private void initManagers() {
        // Kept empty or removed to avoid confusion.
        // Real initialization happens in NetworkService.
    }

    private void setupBottomNavigation() {
        Log.d("DisasterApp", "🔽 setupBottomNavigation() - Setting up bottom nav");
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_map) {
                viewPager.setCurrentItem(0, true);
                return true;
            } else if (itemId == R.id.nav_chat) {
                viewPager.setCurrentItem(1, true);
                return true;
            } else if (itemId == R.id.nav_members) {
                viewPager.setCurrentItem(2, true);
                return true;
            } else if (itemId == R.id.nav_sos) {
                viewPager.setCurrentItem(3, true);
                return true;
            } else if (itemId == R.id.nav_network) {
                viewPager.setCurrentItem(4, true);
                return true;
            }
            return false;
        });

        // Sync ViewPager page swipes → bottom nav highlight
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0: bottomNav.setSelectedItemId(R.id.nav_map); break;
                    case 1: bottomNav.setSelectedItemId(R.id.nav_chat); break;
                    case 2: bottomNav.setSelectedItemId(R.id.nav_members); break;
                    case 3: bottomNav.setSelectedItemId(R.id.nav_sos); break;
                    case 4: bottomNav.setSelectedItemId(R.id.nav_network); break;
                }
            }
        });
    }

    private void setupSosButton() {
        // Add pulse animation to SOS button
        fabSos.post(() -> startSosPulseAnimation());

        fabSos.setOnClickListener(v -> {
            // Vibrate on tap
            if (notificationSoundManager != null) {
                notificationSoundManager.vibrate(50);
            }
            showSosConfirmationDialog();
        });

        // Long press for immediate SOS (bypass confirmation)
        fabSos.setOnLongClickListener(v -> {
            if (notificationSoundManager != null) {
                notificationSoundManager.vibrate(200);
            }
            Toast.makeText(this, "🚨 Emergency SOS - Sending immediately!", Toast.LENGTH_SHORT).show();
            sendSosImmediate();
            return true;
        });
    }

    private void startSosPulseAnimation() {
        // Enhanced pulse animation - more dramatic for emergency button
        android.animation.ObjectAnimator scaleX = android.animation.ObjectAnimator.ofFloat(fabSos, "scaleX", 1f, 1.15f,
                1f);
        android.animation.ObjectAnimator scaleY = android.animation.ObjectAnimator.ofFloat(fabSos, "scaleY", 1f, 1.15f,
                1f);

        // Add glow effect with elevation animation
        android.animation.ObjectAnimator elevation = android.animation.ObjectAnimator.ofFloat(fabSos, "elevation", 12f,
                20f, 12f);

        scaleX.setDuration(1500);
        scaleY.setDuration(1500);
        elevation.setDuration(1500);

        scaleX.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        scaleY.setRepeatCount(android.animation.ObjectAnimator.INFINITE);
        elevation.setRepeatCount(android.animation.ObjectAnimator.INFINITE);

        scaleX.start();
        scaleY.start();
        elevation.start();
    }

    private void showSosConfirmationDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_sos_confirmation, null);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create();


        android.widget.Button btnCancel = dialogView.findViewById(R.id.btnCancelSos);
        // Global SOS Button (Removed to match new theme)
        android.widget.Button btnConfirm = dialogView.findViewById(R.id.btnConfirmSos);

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            btnConfirm.setEnabled(false);
            sendSosImmediate();
            android.widget.Toast.makeText(MainActivityNew.this, "SOS Broadcast Sent!", android.widget.Toast.LENGTH_LONG).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void sendSosImmediate() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "⚠️ Location permission needed for SOS", Toast.LENGTH_SHORT).show();
            return;
        }

        // Immediate feedback
        if (notificationSoundManager != null) {
            notificationSoundManager.vibrate(500);
        }

        Toast.makeText(this, "🚨 SENDING EMERGENCY SOS...", Toast.LENGTH_SHORT).show();

        locationHelper.getCurrentLocation((lat, lng) -> {
            String content = "🚨 EMERGENCY SOS! Location: " + lat + ", " + lng;
            Message sosMessage = new Message(DeviceUtil.getDeviceId(this), username, Message.Type.SOS, content);
            packetHandler.sendMessage(sosMessage);

            // ALSO start continuous live location sharing for SOS
            com.example.disastercomm.services.LiveLocationService.startSharing(
                    this, 
                    com.example.disastercomm.utils.LiveLocationSharingManager.DURATION_CONTINUOUS
            );

            // Success feedback
            runOnUiThread(() -> {
                Toast.makeText(this, "✅ SOS Sent to " + connectedMembers.size() + " devices!", Toast.LENGTH_LONG)
                        .show();
                if (notificationSoundManager != null) {
                    notificationSoundManager.playSosSound();
                }

                // Flash button
                flashSosButton();
            });
        });
    }

    private void flashSosButton() {
        android.animation.ObjectAnimator flash = android.animation.ObjectAnimator.ofFloat(fabSos, "alpha", 1f, 0.3f,
                1f);
        flash.setDuration(200);
        flash.setRepeatCount(5);
        flash.start();
    }

    private void sendSos() {
        showSosConfirmationDialog();
    }

    @Override
    public void onDeviceConnected(String endpointId, String deviceName) {
        runOnUiThread(() -> {
            String displayName = deviceName;
            String peerId = endpointId; // Default fallback

            // Parse "Name__UUID" format
            if (deviceName.contains("__")) {
                String[] parts = deviceName.split("__");
                if (parts.length == 2) {
                    displayName = parts[0];
                    peerId = parts[1]; // Real UUID for private chat
                }
            }

            // ✅ DEBUG: Log mesh connection
            MessageDebugHelper.logConnection(peerId, displayName, "Mesh", true);
            Log.d("DisasterApp", "════════════════════════════════════════");
            Log.d("DisasterApp", "📡 MESH DEVICE CONNECTED");
            Log.d("DisasterApp", "   Endpoint ID: " + endpointId);
            Log.d("DisasterApp", "   Device Name: " + deviceName);
            Log.d("DisasterApp", "   Display Name: " + displayName);
            Log.d("DisasterApp", "   Peer ID: " + peerId);
            Log.d("DisasterApp", "════════════════════════════════════════");

            addMember(peerId, displayName, "Mesh");
            Toast.makeText(this, "Mesh Connected: " + displayName, Toast.LENGTH_SHORT).show();
            updateConnectionStatus(true);

            // Show notification
            if (notificationHelper != null) {
                notificationHelper.showDeviceConnectedNotification(displayName, "WiFi Direct");
            }

            // ✅ Retry offline messages
            if (packetHandler != null)
                packetHandler.retryOfflineMessages();

            // ✅ Sync broadcast history to new device
            syncBroadcastHistoryToNewDevice(endpointId);
            
            // ✅ Share location with the new peer
            if (locationHelper != null) {
                locationHelper.getCurrentLocation((lat, lng) -> {
                    String locPayload = lat + "," + lng;
                    Message locMsg = new Message(DeviceUtil.getDeviceId(MainActivityNew.this), username, Message.Type.LOCATION_UPDATE, locPayload);
                    locMsg.receiverId = "ALL";
                    if (packetHandler != null) {
                        packetHandler.sendMessage(locMsg);
                    }
                });
            }
        });
    }

    @Override
    public void onDeviceDisconnected(String endpointId) {
        // Limitation: If we stored by UUID, we might have trouble finding which one to
        // remove
        // if we only get endpointId back here.
        // However, generic "onEndpointLost" usually gives endpointId.
        // For now, simpler app logic removes based on endpointId IF mapped, OR we
        // Iterate.
        // But our `connectedMembers` is currently keyed by ID.
        // If we want robust removal, we need a map: EndpointId -> MemberUUID.
        // For this patch, we'll try to remove directly or finding by some way.
        // Actually, MeshNetworkManager calls this with whatever ID it tracked.
        // If MeshNetworkManager tracks endpointId->endpointId mapping, we receive
        // endpointId.
        // We might need to store `Map<String, String> endpointToMemberId` to map back.
        // BUT for keeping this simple: we will assume standard removal for now or rely
        // on
        // list refresh if implemented.
        // The implementation in `addMember` keys `connectedMembers` by the ID passed.
        // If we passed UUID (peerId), we can't remove by endpointId easily.
        // Todo: Add a lookup map.
        runOnUiThread(() -> {
            // Simplification: We blindly try to removal.
            // Ideally we need that lookup.
            // Let's rely on the previous logic behavior or user refresh for now to avoid
            // massive refactor risk in this step.
            MemberItem disconnectedMember = connectedMembers.get(endpointId);
            removeMember(endpointId);
            updateConnectionStatus(connectedMembers.size() > 0);

            // Show notification
            if (notificationHelper != null && disconnectedMember != null) {
                notificationHelper.showDeviceDisconnectedNotification(disconnectedMember.name, "WiFi Direct");
            }
        });
    }

    @Override
    public void onPayloadReceived(String endpointId, byte[] payload) {
        packetHandler.handlePayload(endpointId, payload);
    }

    @Override
    public void onMessageReceived(Message message) {
        runOnUiThread(() -> {
            ChatFragment chatFragment = pagerAdapter.getChatFragment();

            // ✅ Auto-discovery: If we receive a message from someone not in our list, add
            // them!
            if (!connectedMembers.containsKey(message.senderId)
                    && !message.senderId.equals(DeviceUtil.getDeviceId(this))) {
                String source = (bluetoothDeviceMap.containsKey(message.senderId)) ? "Bluetooth" : "Mesh";
                addMember(message.senderId, message.senderName, source);
            }

            // ✅ Handle delivery/read receipts via ChatFragment
            if (message.type == Message.Type.DELIVERY_RECEIPT || message.type == Message.Type.READ_RECEIPT) {
                if (chatFragment != null) {
                    chatFragment.processReceivedMessage(message);
                    if (message.type == Message.Type.DELIVERY_RECEIPT) {
                        notificationSoundManager.playDeliverySound();
                    }
                }
                return;
            }

            // Handle location updates
            if (message.type == Message.Type.LOCATION_UPDATE) {
                try {
                    String[] parts = message.content.split(",");
                    if (parts.length == 2) {
                        double lat = Double.parseDouble(parts[0]);
                        double lng = Double.parseDouble(parts[1]);

                        // Update member location
                        MemberItem member = connectedMembers.get(message.senderId);
                        if (member != null) {
                            member.latitude = lat;
                            member.longitude = lng;
                            updateMemberDistance(member);
                            updateMapFragment(); // ✅ Refresh map markers
                        } else {
                            // If member not found (rare), try to add them or ignore
                            // For now, if we receive location, they SHOULD be in connectedMembers or added
                            // soon.
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }

            // Handle SOS
            if (message.type == Message.Type.SOS) {
                Log.d("DisasterApp", "SOS Received from: " + message.senderId);
                notificationSoundManager.playSosSound();
                // Feed SOS to Rescue Dashboard
                com.example.disastercomm.intelligence.NetworkEventBlackBox.logSOSCreated(
                    message.senderName, message.emergencyCategory, message.priorityScore);
                if (rescueDashboardFragment != null) {
                    rescueDashboardFragment.addOrUpdateIncident(message);
                }
                String name = (message.senderName != null && !message.senderName.isEmpty()) ? message.senderName
                        : message.senderId.substring(0, Math.min(8, message.senderId.length()));
                
                // Parse location from SOS message
                double senderLat = 0;
                double senderLng = 0;
                boolean hasSenderLoc = false;
                try {
                    if (message.content != null && message.content.contains("Location:")) {
                        String locPart = message.content.substring(message.content.indexOf("Location:") + 9).trim();
                        String[] parts = locPart.split(",");
                        if (parts.length == 2) {
                            senderLat = Double.parseDouble(parts[0].trim());
                            senderLng = Double.parseDouble(parts[1].trim());
                            hasSenderLoc = true;
                        }
                    }
                } catch (Exception e) {
                    Log.e("DisasterApp", "Error parsing SOS location", e);
                }

                final boolean finalHasSenderLoc = hasSenderLoc;
                final double finalSenderLat = senderLat;
                final double finalSenderLng = senderLng;

                locationHelper.getCurrentLocation((myLat, myLng) -> {
                    StringBuilder detailedMessage = new StringBuilder();
                    detailedMessage.append(message.content).append("\n\n");
                    
                    if (finalHasSenderLoc) {
                        float[] results = new float[1];
                        android.location.Location.distanceBetween(myLat, myLng, finalSenderLat, finalSenderLng, results);
                        float distanceMeters = results[0];
                        String distanceText = (distanceMeters > 1000) ? String.format(java.util.Locale.US, "%.2f km", distanceMeters / 1000) : String.format(java.util.Locale.US, "%.0f m", distanceMeters);
                        detailedMessage.append("📍 Distance to SOS: ").append(distanceText).append("\n\n");
                    }
                    
                    detailedMessage.append("📡 Active Mesh Nodes (").append(connectedMembers.size()).append("):\n");
                    if (connectedMembers.isEmpty()) {
                        detailedMessage.append("No other nodes connected.\n");
                    } else {
                        for (MemberItem member : connectedMembers.values()) {
                            detailedMessage.append("• ").append(member.name).append(" (").append(member.connectionSource).append(")");
                            if (member.latitude != 0 && member.longitude != 0) {
                                float[] res = new float[1];
                                android.location.Location.distanceBetween(myLat, myLng, member.latitude, member.longitude, res);
                                String dist = (res[0] > 1000) ? String.format(java.util.Locale.US, "%.1f km", res[0] / 1000) : String.format(java.util.Locale.US, "%.0f m", res[0]);
                                detailedMessage.append(" - ").append(dist);
                            }
                            detailedMessage.append("\n");
                        }
                    }

                    runOnUiThread(() -> {
                        // Show Notification
                        if (notificationHelper != null) {
                            notificationHelper.showSosNotification(name, message.content);
                        }

                        new AlertDialog.Builder(MainActivityNew.this)
                                .setTitle("🚨 SOS RECEIVED FROM " + name)
                                .setMessage(detailedMessage.toString())
                                .setPositiveButton("Dismiss", (dialog, which) -> {
                                    if (notificationSoundManager != null) {
                                        notificationSoundManager.stopSosSound();
                                    }
                                })
                                .setOnDismissListener(dialog -> {
                                    if (notificationSoundManager != null) {
                                        notificationSoundManager.stopSosSound();
                                    }
                                })
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .show();
                    });
                });

                // SOS always goes to global chat
                if (chatFragment != null && chatFragment.getRecipientId() == null) {
                    // Use centralized method
                    chatFragment.processReceivedMessage(message);
                }
            } else if (message.type == Message.Type.TEXT) {
                // ✅ FIX: Handle private messages correctly
                String myId = DeviceUtil.getDeviceId(this);
                boolean isGlobalMessage = "ALL".equals(message.receiverId);
                boolean isPrivateForMe = myId.equals(message.receiverId);
                boolean isSentByMe = myId.equals(message.senderId);

                notificationSoundManager.playMessageSound();
                Log.d("DisasterApp", "Text Msg Rx. Sender: " + message.senderId + " Receiver: " + message.receiverId);

                if (chatFragment != null) {
                    String currentRecipientId = chatFragment.getRecipientId();

                    if (isGlobalMessage) {
                        // Global message - show in global chat only
                        if (currentRecipientId == null) {
                            chatFragment.processReceivedMessage(message);
                            Log.d("DisasterApp", "✅ Added GLOBAL message to chat");
                        }
                    } else {
                        // Private message routing
                        String chatPartnerId = null;

                        if (isPrivateForMe) {
                            chatPartnerId = message.senderId; // Show in chat with sender
                        } else if (isSentByMe) {
                            chatPartnerId = message.receiverId; // Show in chat with recipient
                        }

                        // Display if viewing the correct private chat
                        if (chatPartnerId != null && chatPartnerId.equals(currentRecipientId)) {
                            chatFragment.processReceivedMessage(message);
                            MessageDebugHelper.logMessageRouting(message.id, currentRecipientId, chatPartnerId, true);
                            Log.d("DisasterApp", "✅ Added PRIVATE message to member chat with " + chatPartnerId);

                            // Send read receipt for incoming messages
                            if (isPrivateForMe) {
                                Message readReceipt = Message.createReadReceipt(message.id, myId, username);
                                packetHandler.sendMessage(readReceipt);
                            }
                        } else {
                            // ✅ Message not displayed - increment unread count
                            MessageDebugHelper.logMessageRouting(message.id, currentRecipientId, chatPartnerId, false);
                            if (isPrivateForMe && messageCounter != null) {
                                messageCounter.increment(message.senderId);
                                MessageDebugHelper.logUnreadCountUpdate(
                                        message.senderId,
                                        messageCounter.getCount(message.senderId),
                                        "Private message received while chat not open");
                            }
                            Log.d("DisasterApp", "⚠️ Private message - current chat: " + currentRecipientId
                                    + ", needed: " + chatPartnerId);
                            // Show notification since message isn't displayed
                            if (notificationHelper != null && isPrivateForMe) {
                                String senderName = message.senderName != null ? message.senderName
                                        : message.senderId.substring(0, Math.min(8, message.senderId.length()));
                                notificationHelper.showMessageNotification(senderName, message.content,
                                        message.senderId);
                            }
                        }

                        // ✅ Update member with last message preview
                        if (chatPartnerId != null) {
                            MemberItem memberToUpdate = connectedMembers.get(chatPartnerId);
                            if (memberToUpdate != null) {
                                memberToUpdate.lastMessagePreview = message.content;
                                memberToUpdate.lastSeenTimestamp = System.currentTimeMillis();
                                MessageDebugHelper.logMessagePreviewUpdate(chatPartnerId, message.content);
                                updateMembersFragment();
                            }
                        }
                    }
                }
                // ✅ FIX: Handle private messages not displayed - NULL SAFE
                String currentChatRecipient = (chatFragment != null) ? chatFragment.getRecipientId() : null;
                if (isPrivateForMe
                        && (currentChatRecipient == null || !message.senderId.equals(currentChatRecipient))) {
                    String name = (message.senderName != null && !message.senderName.isEmpty()) ? message.senderName
                            : message.senderId.substring(0, Math.min(8, message.senderId.length()));

                    // Show Notification
                    if (notificationHelper != null) {
                        notificationHelper.showMessageNotification(name, message.content, message.senderId);
                    }
                    Toast.makeText(this, "💬 Private message from " + name, Toast.LENGTH_LONG).show();
                }
            } else if (message.type == Message.Type.KEY_EXCHANGE) {
                Log.d("DisasterApp", "Key Exchange Received from: " + message.senderId);
                MemberItem member = connectedMembers.get(message.senderId);
                if (member != null) {
                    member.isSecure = true;
                    updateMembersFragment();
                    String name = (message.senderName != null) ? message.senderName : "Peer";
                    Toast.makeText(this, "🔐 Secure connection established with " + name, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void addMember(String id, String name, String type) {
        if (routingTable != null) {
            routingTable.addNeighbor(id, name);
        }
        
        MemberItem member = new MemberItem(id, name);
        member.connectionSource = type; // ✅ Set connection source

        // Initialize coverage range and default signal
        if ("Bluetooth".equalsIgnoreCase(type)) {
            member.coverageRange = 30; // 30m for Bluetooth
        } else {
            member.coverageRange = 500; // 500m for Mesh/WiFi Direct
        }

        // Initial signal calculation (mock distance if 0)
        if (member.distance == 0) {
            member.distance = (int) (Math.random() * (member.coverageRange * 0.8)); // Random distance within 80% of
                                                                                    // range
        }

        // Calculate signal quality based on coverage range
        int signalPercentage = (int) ((1.0 - (double) member.distance / member.coverageRange) * 100);
        signalPercentage = Math.max(0, Math.min(100, signalPercentage)); // Clamp 0-100
        member.connectionQuality = signalPercentage;

        if (signalPercentage >= 75) {
            member.signalStrength = "Strong";
        } else if (signalPercentage >= 35) {
            member.signalStrength = "Medium";
        } else {
            member.signalStrength = "Weak";
        }

        // ✅ Load unread count from MessageCounter
        if (messageCounter != null) {
            member.unreadCount = messageCounter.getCount(id);
        }

        // Check if we already have a secure key for this user
        if (packetHandler != null && packetHandler.hasPublicKey(id)) {
            member.isSecure = true;
        }
        connectedMembers.put(id, member);
        updateMembersFragment();
        updateMapFragment();
        updateMapStatusCards();

        // ✅ Notify Global Chat
        ChatFragment chatFragment = pagerAdapter.getChatFragment();
        if (chatFragment != null) {
            chatFragment.updatePrivateChatsList();
            if (chatFragment.getRecipientId() == null) {
                chatFragment.addSystemMessage("🔵 " + name + " joined via " + type);
            }
        }

        // ✅ Notify Private Chat if open
        if (privateChatFragment != null && privateChatFragment.isVisible()) {
            String currentRecipient = privateChatFragment.getRecipientId();
            if (currentRecipient != null && currentRecipient.equals(id)) {
                privateChatFragment.addSystemMessage("🔵 " + name + " is now Online (" + type + ")");
            }
        }
    }

    private void removeMember(String id) {
        if (routingTable != null) {
            routingTable.removeNeighbor(id);
        }
        
        MemberItem member = connectedMembers.get(id);
        String name = (member != null) ? member.name : "Unknown Device";

        connectedMembers.remove(id);
        updateMembersFragment();
        updateMapFragment();
        updateMapStatusCards();

        // ✅ Notify Global Chat
        ChatFragment chatFragment = pagerAdapter.getChatFragment();
        if (chatFragment != null) {
            chatFragment.updatePrivateChatsList();
            if (chatFragment.getRecipientId() == null) {
                chatFragment.addSystemMessage("⚫ " + name + " disconnected");
            }
        }

        // ✅ Notify Private Chat if open
        if (privateChatFragment != null && privateChatFragment.isVisible()) {
            String currentRecipient = privateChatFragment.getRecipientId();
            if (currentRecipient != null && currentRecipient.equals(id)) {
                privateChatFragment.addSystemMessage("⚫ " + name + " is now Offline");
            }
        }
    }

    private void updateMapStatusCards() {
        MapFragment mapFragment = pagerAdapter.getMapFragment();
        if (mapFragment != null) {
            mapFragment.updateBluetoothStatus(bluetoothDeviceMap.size(), true);

            if (connectedMembers.isEmpty()) {
                mapFragment.updateSignalQuality("Weak", "~0m range");
            } else {
                // Find the member with the best connection quality
                MemberItem bestMember = null;
                for (MemberItem member : connectedMembers.values()) {
                    if (bestMember == null || member.connectionQuality > bestMember.connectionQuality) {
                        bestMember = member;
                    }
                }

                if (bestMember != null) {
                    String rangeText = "~" + bestMember.coverageRange + "m range";
                    Log.d("DisasterApp",
                            "📊 Update Signal Card: " + bestMember.signalStrength + " (" + rangeText + ")"); // DEBUG
                                                                                                             // LOG
                    mapFragment.updateSignalQuality(bestMember.signalStrength, rangeText);
                }
            }
        }
    }

    private void updateMemberDistance(MemberItem member) {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationHelper.getCurrentLocation((myLat, myLng) -> {
                if (member.latitude != 0 && member.longitude != 0) {
                    float[] results = new float[1];
                    Location.distanceBetween(myLat, myLng, member.latitude, member.longitude, results);
                    member.distance = (int) results[0];

                    // ✅ Recalculate signal quality based on new distance
                    if (member.coverageRange > 0) {
                        int signalPercentage = (int) ((1.0 - (double) member.distance / member.coverageRange) * 100);
                        signalPercentage = Math.max(0, Math.min(100, signalPercentage)); // Clamp 0-100

                        Log.d("DisasterApp", "📡 Signal Calc - Member: " + member.name +
                                ", Dist: " + member.distance + "m, Range: " + member.coverageRange +
                                "m, Quality: " + signalPercentage + "%"); // DEBUG LOG

                        member.connectionQuality = signalPercentage;

                        if (signalPercentage >= 75) {
                            member.signalStrength = "Strong";
                        } else if (signalPercentage >= 35) {
                            member.signalStrength = "Medium";
                        } else {
                            member.signalStrength = "Weak";
                        }
                    }

                    updateMembersFragment();
                    updateMapStatusCards(); // Refresh signal card
                }
            });
        }
    }

    private void updateMembersFragment() {
        MembersFragment membersFragment = pagerAdapter.getMembersFragment();
        if (membersFragment != null) {
            List<MemberItem> memberList = new ArrayList<>(connectedMembers.values());
            
            // Sync battery levels from RoutingTable
            for (MemberItem m : memberList) {
                if (routingTable != null) {
                    com.example.disastercomm.network.MeshRoutingTable.NeighborInfo node = routingTable.getNeighbors().get(m.id);
                    if (node != null) {
                        m.batteryLevel = node.batteryLevel;
                    }
                }
            }

            Log.d("DisasterApp", "📡 UPDATING MEMBERS FRAGMENT with " + memberList.size() + " members");
            for (MemberItem m : memberList) {
                Log.d("DisasterApp", "   - " + m.name + " (" + m.connectionSource + ", " +
                        (m.isOnline ? "Online" : "Offline") + ")");
            }
            membersFragment.updateMembers(memberList);
        } else {
            Log.w("DisasterApp", "⚠️ MembersFragment is NULL - cannot update!");
        }
    }

    public void refreshMembersFragment() {
        MembersFragment membersFragment = pagerAdapter.getMembersFragment();
        if (membersFragment != null) {
            membersFragment.updateMembers(new ArrayList<>(connectedMembers.values()));
        }
    }

    private void updateMapFragment() {
        MapFragment mapFragment = pagerAdapter.getMapFragment();
        if (mapFragment != null) {
            mapFragment.updateMeshStatus(connectedMembers.size());
            // ✅ Show members on map
            mapFragment.updateMembersOnMap(new ArrayList<>(connectedMembers.values()));

            // ✅ Set listener for clicks (if not alreadyset)
            mapFragment.setOnMapMemberClickListener((id, name) -> {
                MemberItem item = connectedMembers.get(id);
                if (item == null)
                    item = new MemberItem(id, name);
                openPrivateChat(item);
            });
        }
    }

    private void updateConnectionStatus(boolean connected) {
        if (tvConnectionStatus != null) {
            tvConnectionStatus.setText(connected ? "Connected" : "Offline");
            if (viewStatusDot != null) {
                viewStatusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        getResources().getColor(connected ? R.color.connected_green : R.color.signal_weak, null)));
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // ✅ Save username to ensure it persists across activity recreation
        outState.putString("username", username);
        Log.d("DisasterApp", "💾 State saved - username: " + username);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // ✅ Restore username - ensures device name never changes
        if (savedInstanceState.containsKey("username")) {
            username = savedInstanceState.getString("username");
            Log.d("DisasterApp", "♻️ State restored - username: " + username);

            // Update navigation drawer header with restored username
            if (navigationView != null) {
                View headerView = navigationView.getHeaderView(0);
                if (headerView != null) {
                    TextView tvUserName = headerView.findViewById(R.id.tvUserName);
                    TextView tvUserInitial = headerView.findViewById(R.id.tvUserInitial);
                    TextView tvUserRole = headerView.findViewById(R.id.tvUserRole);
                    
                    if (tvUserName != null)
                        tvUserName.setText(username);
                    if (tvUserInitial != null && username != null && !username.isEmpty())
                        tvUserInitial.setText(username.substring(0, 1).toUpperCase());
                        
                    if (tvUserRole != null) {
                        String role = getSharedPreferences("UserProfile", MODE_PRIVATE).getString("role", "Civilian");
                        tvUserRole.setText(role);
                    }
                }
            }
        }
    }

    // ✅ SYNC: Sync recent broadcast history to new device
    private void syncBroadcastHistoryToNewDevice(String recipientId) {
        Log.d("DisasterApp", "🔄 SYNC: Starting history sync for new device: " + recipientId);

        com.example.disastercomm.data.AppDatabase db = com.example.disastercomm.data.AppDatabase.getDatabase(this);
        com.example.disastercomm.data.AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                // 1. Fetch recent broadcasts (limit 20)
                java.util.List<Message> recentBroadcasts = db.messageDao().getRecentGlobalMessages(20);

                if (recentBroadcasts != null && !recentBroadcasts.isEmpty()) {
                    Log.d("DisasterApp", "   → Found " + recentBroadcasts.size() + " broadcast messages to sync");

                    // Sort by timestamp (oldest first) so they arrive in order
                    java.util.Collections.sort(recentBroadcasts, (m1, m2) -> Long.compare(m1.timestamp, m2.timestamp));

                    for (Message originalMsg : recentBroadcasts) {
                        // 2. Clone message to avoid modifying DB entity
                        Message syncMsg = new Message();
                        syncMsg.id = originalMsg.id; // Keep original ID (deduplication)
                        syncMsg.senderId = originalMsg.senderId;
                        syncMsg.senderName = originalMsg.senderName;
                        syncMsg.content = originalMsg.content;
                        syncMsg.timestamp = originalMsg.timestamp;
                        syncMsg.type = originalMsg.type;
                        syncMsg.isHistorySync = true; // ✅ Mark as history sync

                        // 3. TARGET: Send directly to the NEW device, but keep "ALL" as visible
                        // receiver
                        // The trick: We Unicast it to `recipientId`, but set `receiverId` to "ALL"
                        // inside the payload object.
                        syncMsg.receiverId = "ALL";

                        // But we want to route ONLY to `recipientId`.
                        // Using `meshNetworkManager.sendPayload()` directly bypasses the broadcast
                        // check!

                        String json = new com.google.gson.Gson().toJson(syncMsg);
                        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);

                        if (meshNetworkManager != null) {
                            meshNetworkManager.sendPayload(recipientId, bytes);
                        }
                        // Small delay to prevent flooding
                        try {
                            Thread.sleep(50);
                        } catch (Exception e) {
                        }
                    }
                    Log.d("DisasterApp", "✅ SYNC: History sync completed for " + recipientId);
                } else {
                    Log.d("DisasterApp", "   → No recent broadcasts found to sync");
                }
            } catch (Exception e) {
                Log.e("DisasterApp", "❌ SYNC: Failed to sync history", e);
            }
        });
    }

    private void setupConnectivityStatus() {
        connectivityStatusManager = new com.example.disastercomm.utils.ConnectivityStatusManager(this);

        // Set listener for status changes
        connectivityStatusManager.setListener(this::updateConnectivityIcons);

        // Initial update
        updateConnectivityIcons();
    }

    private void requestEnable(String type, Runnable action) {
        new AlertDialog.Builder(this)
                .setTitle(type + " Disabled")
                .setMessage(type + " is required/enhanced for mesh networking. Enable it now?")
                .setPositiveButton("Enable", (dialog, which) -> action.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showNetworkDiagnosisDialog() {
        if (mService == null) {
            Toast.makeText(this, "Network Service not bound", Toast.LENGTH_SHORT).show();
            return;
        }

        String report = mService.getNetworkStrengthReport();

        new AlertDialog.Builder(this)
                .setTitle("📡 Network Strength / Diagnosis")
                .setMessage(report)
                .setPositiveButton("OK", null)
                .setNeutralButton("Refresh Scan", (dialog, which) -> {
                    triggerManualDeviceScan();
                    Toast.makeText(this, "Rescanning...", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void updateConnectivityIcons() {
        // UI removed in redesign
    }

    private void setupUpdateNotification() {
        viewUpdateBadge.setVisibility(View.GONE);
    }

    private void checkForUpdatesWithBadge() {
        com.example.disastercomm.utils.UpdateManager updateManager = new com.example.disastercomm.utils.UpdateManager(
                this);

        updateManager.fetchLatestVersion(new com.example.disastercomm.utils.UpdateManager.VersionCallback() {
            @Override
            public void onVersionFetched(String version, boolean isNewer) {
                if (isNewer && viewUpdateBadge != null) {
                    viewUpdateBadge.setVisibility(View.VISIBLE);
                } else if (!isNewer && viewUpdateBadge != null) {
                    viewUpdateBadge.setVisibility(View.GONE);
                }
            }

            @Override
            public void onError(String error) {
                // Silently fail - just hide badge
                if (viewUpdateBadge != null) {
                    viewUpdateBadge.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Prevent memory leak
        if (packetHandler != null) {
            packetHandler.setMessageListener(null);
        }

        if (mBound) {
            unbindService(connection);
            mBound = false;
        }
        try {
            unregisterReceiver(networkReceiver);
        } catch (IllegalArgumentException e) {
            // Not registered
        }

        // Note: We do NOT stop the managers here, because the Service keeps them alive!
        // This is the key benefit of the refactor.

        if (notificationSoundManager != null)
            notificationSoundManager.release();
        if (networkStateMonitor != null)
            networkStateMonitor.stopMonitoring();
    }

    // ──────────────────────────────────────────────────────────────────────────────
    // Hackathon Blueprint: Public APIs for Fragments
    // ──────────────────────────────────────────────────────────────────────────────

    /**
     * Called by SOSFragment to broadcast a pre-built smart SOS message via the mesh.
     */
    public void broadcastSOSMessage(Message sos) {
        // Automatically start continuous live location tracking for SOS
        com.example.disastercomm.services.LiveLocationService.startSharing(
                this, 
                com.example.disastercomm.utils.LiveLocationSharingManager.DURATION_CONTINUOUS
        );
        
        com.example.disastercomm.utils.LocationHelper locationHelper = new com.example.disastercomm.utils.LocationHelper(this);
        locationHelper.getCurrentLocation((lat, lng) -> {
            // Append location to the SOS message content and set explicit coordinates
            sos.latitude = lat;
            sos.longitude = lng;
            sos.content = sos.content + " | Location: " + lat + ", " + lng;
            
            if (packetHandler != null) {
                packetHandler.sendMessage(sos);
            }
            // Also push to own rescue dashboard
            if (rescueDashboardFragment != null) {
                rescueDashboardFragment.addOrUpdateIncident(sos);
            }
        });
    }

    /**
     * Returns the MeshRoutingTable for the NetworkDashboardFragment.
     */
    public com.example.disastercomm.network.MeshRoutingTable getRoutingTable() {
        return routingTable;
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            android.view.View v = getCurrentFocus();
            if (v instanceof android.widget.EditText) {
                android.graphics.Rect outRect = new android.graphics.Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) ev.getRawX(), (int) ev.getRawY())) {
                    v.clearFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(ev);
    }
}

