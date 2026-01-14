# DisasterComm User Guide

## Complete Step-by-Step Guide for Emergency Communication

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Connecting Devices](#connecting-devices)
3. [Communication Guide](#communication-guide)
4. [Emergency SOS](#emergency-sos)
5. [Location Features](#location-features)
6. [Network Management](#network-management)
7. [Troubleshooting](#troubleshooting)
8. [Best Practices](#best-practices)

---

## 1. Getting Started

### Installation

#### Installing from APK File

1. **Download APK**:
   - Get `app-debug.apk` from your source
   - Transfer to your Android phone

2. **Enable Unknown Sources**:
   - Go to **Settings** → **Security**
   - Enable **"Install from Unknown Sources"** or **"Install Unknown Apps"**
   - On Android 8+: Allow installation for your file manager

3. **Install**:
   - Open file manager
   - Tap on the APK file
   - Tap **Install**
   - Wait for installation to complete

4. **Launch**:
   - Tap **Open** after installation
   - Or find "Disaster Comm" in your app drawer

### First-Time Setup

#### Step 1: Choose Username

<img src="docs/screenshots/login_screen.png" width="300"/>

- Enter a simple, memorable name (e.g., "John", "Rescue1", "Family_Dad")
- No password needed
- No registration required
- Can be changed later in Settings

**Tips**:
- Use your real first name in family groups
- Use role names in rescue operations (e.g., "Medic", "Leader")
- Keep it short (5-15 characters)

#### Step 2: Grant Permissions

The app will request several permissions. **All are required** for the mesh network to function:

##### Location Permission

```
"Allow DisasterComm to access this device's location?"
→ Tap "While using the app" or "Always"
```

**Why needed**: WiFi Direct requires location permission (Android requirement, not app requirement)

##### Bluetooth Permission

```
"Allow DisasterComm to connect to Bluetooth devices?"
→ Tap "Allow"
```

**Why needed**: For Bluetooth mesh connections

##### Notification Permission (Android 13+)

```
"Allow notifications from DisasterComm?"
→ Tap "Allow"
```

**Why needed**: To alert you of messages, connections, and emergencies

#### Step 3: Network Services Start

After granting permissions:
- You'll see: **"Network services are active"** notification
- Connection status shows: **"Searching..."**
- The app begins discovering nearby devices

---

## 2. Connecting Devices

### Understand Connection Process

DisasterComm uses **two connection methods simultaneously**:

1. **WiFi Direct** (Primary)
   - Range: ~100 meters
   - Speed: Fast (100+ Mbps)
   - Best for: Most scenarios

2. **Bluetooth** (Backup)
   - Range: ~10-30 meters
   - Speed: Moderate (2-3 Mbps)
   - Best for: Close-range, WiFi unavailable

### Automatic Connection (Recommended)

**Both devices must**:
1. Have DisasterComm installed and open
2. Have granted all permissions
3. Have location services ON
4. Have WiFi and Bluetooth enabled

**Connection happens automatically**:
1. Open the app on both devices
2. Wait 10-30 seconds
3. You'll see a toast: **"Mesh Connected: [Username]"**
4. Connection indicator turns **green**

### Checking Connections

#### Method 1: Connection Status Indicator

Look at the top of the main screen:
- **Green dot** + "Connected" = You're connected to the mesh
- **Red dot** + "Searching..." = No connections yet

#### Method 2: Members Tab

1. Tap **Members** icon (bottom navigation, person icon)
2. See list of all connected devices
3. Each shows:
   - Username
   - Distance from you
   - Signal strength
   - Connection type (WiFi/Bluetooth)

#### Method 3: Navigation Drawer

1. Tap **☰** (hamburger menu, top-left)
2. Select **"Nearby Devices"**
3. See detailed list:
   ```
   📍 Nearby Devices (WiFi Direct):
   
   📍 John
      Distance: 45m
      Signal: Strong
   
   📍 Sarah
      Distance: 120m
      Signal: Moderate
   ```

### Troubleshooting Connections

If devices won't connect:

1. **Verify Prerequisites**:
   - ✅ Both have app installed
   - ✅ Both granted ALL permissions
   - ✅ Both have location turned ON
   - ✅ Both have WiFi enabled
   - ✅ Both have Bluetooth enabled

2. **Restart Discovery**:
   - On both devices: Close and reopen the app
   - Wait 30 seconds

3. **Check Distance**:
   - Move devices closer (within 10 meters)
   - Remove obstacles (walls, metal objects)

4. **Manual Bluetooth Pairing** (if needed):
   - Settings → Bluetooth
   - Pair devices manually
   - Return to DisasterComm

---

## 3. Communication Guide

### Global Chat (Broadcast to Everyone)

**Use for**: General announcements, status updates, group coordination

#### Sending Global Messages

1. **Navigate to Chat Tab**:
   - Tap **Chat** icon (bottom navigation, speech bubble)

2. **Type Message**:
   - Tap text input field at bottom
   - Type your message
   - Keep it concise and clear

3. **Send**:
   - Tap **✈️** (send) icon
   - Message broadcasts to all connected devices
   - You'll see your message appear with a checkmark

#### Message Indicators

- **✓** (single check): Sent from your device
- **✓✓** (double check): Delivered to at least one device
- **Blue checkmarks**: Read by recipient(s)

#### Example Global Chat Use Cases

```
Scenario: Earthquake aftermath, coordinating supplies

User "Sarah": "We have water at the school. 100 bottles available."
User "Mike": "Medical supplies needed at 5th Street"
User "Rescue1": "All clear at Main Street. Moving to Park Avenue."
```

### Private Chat (One-on-One)

**Use for**: Personal conversations, specific coordination, sensitive information

#### Starting Private Chat

1. **Go to Members Tab**:
   - Tap **Members** icon (bottom)

2. **Select Person**:
   - Tap on the person you want to chat with
   - Their profile opens

3. **Private Chat Opens**:
   - New overlay appears
   - Only you and selected person see these messages
   - Messages are end-to-end encrypted

4. **Type and Send**:
   - Type in the input field
   - Tap send
   - Message only goes to that person

#### Closing Private Chat

- Tap **X** or back button
- Returns to main view
- Chat history saved

#### Example Private Chat

```
You → John: "Are you okay? Your location shows you're far."
John → You: "Yes, I'm safe. Phone battery low, will update soon."
```

### Message Features

#### Viewing Message History

- **Global Chat**: Shows last 100 messages
- **Private Chats**: Shows all messages with that person
- **Scroll up** to see older messages

#### Message Search (Future Feature)

Currently: Scroll through history  
Planned: Search by keyword, date, sender

#### Message Types

The app supports several message types:

1. **TEXT**: Regular chat messages
2. **SOS**: Emergency alerts (see Emergency SOS section)
3. **LOCATION_UPDATE**: GPS coordinates
4. **DELIVERY_RECEIPT**: Confirmation of delivery
5. **READ_RECEIPT**: Confirmation of reading

---

## 4. Emergency SOS

### When to Use SOS

Use the **SOS button** for:
- ✅ Life-threatening emergencies
- ✅ Serious injuries requiring immediate help
- ✅ Being trapped or immobilized
- ✅ Natural disaster impact
- ✅ Critical resource needs (food, water, medical)

**Do NOT use for**:
- ❌ Testing the app
- ❌ Minor issues
- ❌ General questions

### Sending SOS

#### Method 1: Quick SOS (Long Press)

**For immediate emergencies**:

1. **Long press** the red **SOS** button (bottom-right of main screen)
2. SOS **immediately broadcasts** without confirmation
3. Your **GPS location** is automatically included
4. All devices receive:
   ```
   🚨 SOS RECEIVED
   SOS from John: 🚨 EMERGENCY SOS! 
   Location: 37.7749, -122.4194
   ```

#### Method 2: Confirmed SOS (Single Tap)

**With confirmation countdown**:

1. **Tap** the red **SOS** button once
2. **Confirmation dialog** appears
3. Review message:
   ```
   ⚠️ Send Emergency SOS?
   This will broadcast your location to all devices
   ```
4. **Tap "Confirm"**
5. **3-second countdown** begins
6. Can cancel during countdown
7. SOS broadcasts after countdown

### Receiving SOS

When someone sends SOS:

1. **Alert Dialog** pops up:
   ```
   🚨 SOS RECEIVED
   SOS from Sarah: 🚨 EMERGENCY SOS! Location: 37.7749, -122.4194
   
   [VIEW ON MAP]  [OK]
   ```

2. **Notification** appears in status bar

3. **Sound & Vibration** alert you

4. **Actions**:
   - Tap **"View on Map"**: See exact location
   - Tap **"OK"**: Acknowledge and close
   - Open app: See SOS in global chat

### SOS Best Practices

1. **Include Details**:
   - Before sending, note what you need:
     - "Injured, need medical help"
     - "Trapped under debris at building entrance"
     - "Out of water, 3 children with me"

2. **Stay Calm**:
   - SOS is sent to EVERYONE in mesh
   - Someone will respond
   - Keep phone on for location tracking

3. **Save Battery**:
   - After sending SOS, reduce phone usage
   - Enable battery saver mode
   - Keep GPS on for rescuers

---

## 5. Location Features

### Viewing Map

1. **Navigate to Map Tab**:
   - Tap **Map** icon (bottom navigation, leftmost)

2. **Your Location**:
   - **Blue marker** (📍) = You
   - Updates in real-time

3. **Other Users**:
   - **Red markers** (📍) = Other connected users
   - Tap marker to see:
     - Username
     - Distance from you
     - Last update time

### Sharing Location

Location is shared **automatically** when:
- You connect to the mesh
- Every 60 seconds while connected
- When you send a message
- When you send SOS

**Manual location update**:
1. Open Navigation Drawer (☰)
2. Tap "Share Location Now" (if available)

### Location Privacy

- Location only shared **within the mesh network**
- **Not uploaded** to any server
- **Not stored** on other devices longer than necessary
- **Turn off** by disabling GPS (but reduces functionality)

### Distance to Others

In **Members Tab**, see distance to each person:
- **"< 50m"**: Very close
- **"120m"**: Medium distance
- **"500m+"**: Far (through mesh relay)
- **"Unknown"**: No location data yet

---

## 6. Network Management

### Viewing Network Status

#### Connection Overview

1. Open **Navigation Drawer** (☰)
2. Tap **"Network Status"**
3. See:
   ```
   Available Networks:
   Wi-Fi Direct: Active
   Bluetooth: Active
   Internet: Not Available
   
   Internet Status: Not Available
   ```

#### Bluetooth Devices

1. Open Navigation Drawer
2. Tap **"Bluetooth Devices"**
3. See all Bluetooth connections:
   ```
   Discovered Bluetooth Devices:
   
   📲 Galaxy S10
      Address: AA:BB:CC:DD:EE:FF
   
   📲 Pixel 6
      Address: 11:22:33:44:55:66
   ```

#### Nearby Devices (WiFi Direct)

1. Open Navigation Drawer
2. Tap **"Nearby Devices"**
3. Detailed mesh information:
   ```
   Nearby Devices (WiFi Direct):
   
   📍 John
      Distance: 45m
      Signal: Strong
   
   📍 Sarah
      Distance: 120m via relay
      Signal: Moderate
   ```

### Managing Connections

#### Disconnecting from Network

- Simply **close the app** or
- **Turn off WiFi/Bluetooth**
- Others will see you as "offline"

#### Reconnecting

- **Open app** again
- Automatic discovery resumes
- Previous conversations restored from local storage

### Network Optimization

#### For Best Performance

1. **Keep WiFi On**: Even without internet
2. **Enable Bluetooth**: For fallback
3. **Location Services On**: Required for WiFi Direct
4. **Keep App Open**: Or allow background running

#### Battery Saving Mode

If low on battery:
1. **Disable Bluetooth**: Use only WiFi Direct
2. **Reduce Screen Brightness**
3. **Close Other Apps**
4. **Enable Battery Saver** (Android settings)

---

## 7. Troubleshooting

### Common Issues

#### Issue: "Cannot Connect to Other Devices"

**Solutions**:

1. ✅ Check permissions:
   - Settings → Apps → DisasterComm → Permissions
   - Ensure all are granted

2. ✅ Enable location:
   - Settings → Location → Turn ON

3. ✅ Check WiFi:
   - Settings → WiFi → Turn ON
   - Don't need to connect to a network, just enable WiFi

4. ✅ Check Bluetooth:
   - Settings → Bluetooth → Turn ON

5. ✅ Restart app on both devices

6. ✅ Move closer (within 10 meters initially)

#### Issue: "Messages Not Sending"

**Check**:
1. Connection status (should be green)
2. At least one device in Members list
3. Network services active (check notification)

**Solutions**:
1. Tap message again to resend
2. Restart app
3. Check logcat (for developers)

#### Issue: "Location Not Updating"

**Solutions**:
1. Enable GPS: Settings → Location → High Accuracy
2. Go outdoors (better GPS signal)
3. Grant location permission: Always (not just "While Using")

#### Issue: "Battery Draining Fast"

**Expected**: Mesh networking uses power

**Reduce drain**:
1. Lower screen brightness
2. Disable Bluetooth if only using WiFi Direct
3. Close other apps
4. Use power saving mode

#### Issue: "App Crashes on Startup"

**Solutions**:
1. Clear app cache:
   - Settings → Apps → DisasterComm → Storage → Clear Cache

2. Reinstall app:
   - Uninstall
   - Install fresh APK

3. Check Android version (requires Android 5.0+)

### Getting Help

#### In-App Logs

For developers:
```bash
adb logcat -s DisasterApp:* NotificationHelper:* AndroidRuntime:E
```

#### Report Issues

Include:
- Android version
- Device model
- Steps to reproduce
- Logcat output (if possible)

---

## 8. Best Practices

### For Maximum Network Range

1. **Spread Out**:
   - Don't cluster all devices together
   - Spacing creates mesh relay points
   - Optimal: 50-80 meters between devices

   ```
   ❌ Bad: [A][B][C][D]          (clustered)
   ✅ Good: [A]---[B]---[C]---[D] (spread out)
   ```

2. **Avoid Obstacles**:
   - Metal structures block signals
   - Concrete walls reduce range
   - Open areas work best

3. **Keep Elevation**:
   - Higher devices have better range
   - Avoid basements
   - Use upper floors when possible

### For Emergency Situations

1. **Install Before Disaster**:
   - Download and install during normal times
   - Test with family/friends
   - Share APK with community

2. **Keep Phone Charged**:
   - Charge fully before disaster
   - Carry power bank
   - Use battery saver mode

3. **Coordinate Groups**:
   - Designate communication leads
   - Establish check-in times
   - Use consistent usernames (Family1, Family2, etc.)

4. **Information Sharing**:
   - Share critical info early: "Water at School Park"
   - Use concise messages
   - Confirm important messages: "Received, we're 10 min away"

### For Rescue Operations

1. **Grid Pattern**:
   - Position devices in grid formation
   - Maximum coverage area
   - Redundant paths

   ```
   [A]----[B]----[C]
    |      |      |
   [D]----[E]----[F]
    |      |      |
   [G]----[H]----[I]
   
   9 devices covering large area
   Multiple routes for messages
   ```

2. **Role-Based Usernames**:
   - "Medic1", "Medic2"
   - "SearchTeam1", "SearchTeam2"
   - "Coordinator"

3. **Regular Updates**:
   - Broadcast status every 30 minutes
   - Share resource availability
   - Report hazards

---

## Quick Reference Card

### Essential Actions

| Action | How To |
|--------|--------|
| Send Global Message | Chat Tab → Type → Send |
| Send Private Message | Members → Tap Person → Type → Send |
| Send Quick SOS | Long press SOS button |
| View Map | Map Tab (bottom left)|
| Check Connections | Members Tab or ☰ → Nearby Devices |
| View Network Status | ☰ → Network Status |

### Interpreting Icons

| Icon | Meaning |
|------|---------|
| 🟢 Green dot | Connected to mesh |
| 🔴 Red dot | Not connected |
| ✓ | Message sent |
| ✓✓ | Message delivered |
| 📍 | Location marker |
| 🚨 | Emergency SOS |
| ☰ | Navigation menu |

### Emergency Contacts

In real emergencies:
- **Call 911** (if cell service available)
- **Use DisasterComm** (if no cell service)
- **Signal for help** (visual signals, flares)

---

## Appendix: Scenarios

### Scenario 1: Earthquake Aftermath

**Situation**: Cell towers down, family separated

1. **Sarah** (mom) at home – opens DisasterComm
2. **John** (dad) at office – opens DisasterComm
3. They connect automatically within 30 seconds
4. **Sarah**: "Are you okay?"
5. **John**: "Yes, building safe. Where are kids?"
6. **Sarah**: "Kids with me, heading to park meeting point"
7. **John**: Sees Sarah's location on map, navigates to her

### Scenario 2: Rescue Operation

**Situation**: Building collapse, coordinating rescue

1. **Coordinator** at command post
2. **SearchTeam1**, **SearchTeam2** in field
3. **Medic1**, **Medic2** on standby

```
SearchTeam1: "Survivor found, grid location B3"
Coordinator: "Medic1, respond to B3"
Medic1: "En route, ETA 5 min"
Coordinator: "SearchTeam2, continue sweep of D quadrant"
SearchTeam2: "Copy, moving to D"
```

### Scenario 3: Remote Hiking

**Situation**: Lost hiker, no cell service

1. Hikers spread out in search pattern
2. Maintain DisasterComm connection
3. Cover larger area while staying in communication
4. When found: Send exact GPS coordinates to all

---

## Stay Safe, Stay Connected

DisasterComm is a tool – **your judgment is the most important asset in an emergency**.

- Use wisely
- Help others
- Share knowledge
- Stay prepared

**Remember**: The network is only as strong as its participants. Every device running DisasterComm extends the range and reliability of the mesh.
