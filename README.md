# Disaster Communication System v5.16.0

<p align="center">
  <img src="app/src/main/res/drawable/ic_app_logo.png" alt="DisasterComm Logo" width="120"/>
</p>

<p align="center">
  <strong>Emergency Mesh Network for Disaster Scenarios</strong><br>
  Communication without Internet, Cell Towers, or Central Infrastructure
</p>

---

## 📖 Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [How It Works](#how-it-works)
- [Technical Architecture](#technical-architecture)
- [Rescue Node Hub Mode](#rescue-node-hub-mode)
- [Hardware Extensions](#hardware-extensions)
- [Getting Started](#getting-started)
- [Usage Guide](#usage-guide)
- [Technical Specifications](#technical-specifications)
- [Project Structure](#project-structure)
- [Development](#development)
- [FAQ](#faq)

---

## 🌍 Overview

**DisasterComm** is an Android application designed for emergency communication when traditional infrastructure fails. During disasters like earthquakes, floods, or hurricanes, cell towers and internet connectivity are often unavailable. This app creates a peer-to-peer mesh network using WiFi Direct and Bluetooth, allowing people to communicate, broadcast SOS, and share real-time location data without any central infrastructure.

### The Problem It Solves

- 📵 Cell towers down = No phone calls or SMS
- 🌐 Internet offline = No WhatsApp, Email, or social media
- 🔌 Power outages = No home WiFi routers
- 💡 **Solution**: Direct device-to-device communication with automatic message relay and captive portal alerts.

### Real-World Use Cases

1. **Natural Disasters**: Earthquakes, floods, hurricanes, tornadoes
2. **Remote Areas**: Hiking, camping, wilderness exploration
3. **Protests & Public Gatherings**: Communication without centralized monitoring
4. **Network Infrastructure Failures**: During outages or maintenance
5. **Emergency Services**: First responders in disaster zones

---

## 🔑 Key Features (Updated in v5.16.0)

- **Rescue Node Hub Topology:** Devices configured as "Rescue Team" automatically act as central always-on hubs, forcing auto-discovery of all nearby nodes without entering low-power states.
- **Offline Photo Sharing:** Capture and transmit highly-compressed thumbnail images over the decentralized mesh network without exceeding strict 32KB payload limits.
- **Open-Source Map Tiles:** Full integration with free, non-watermarked OpenStreetMap (Mapnik) tiles for API key-free offline tracking.
- **App-less Emergency Captive Portals:** Users without the app can connect to open emergency WiFi networks to receive SMS-style emergency alerts and an offline APK download link via a captive portal.
- **Role-Based Access Control (RBAC):** Users choose between "Civilian" and "Rescue Team" during login. Rescue Teams get exclusive access to the specialized Rescue Dashboard.
- **Rescue Dashboard:** A dedicated command-center view for First Responders that loads historical and active SOS requests from local Room database storage.
- **High-Accuracy Live Location Tracking:** Continuous background GPS polling (down to 2-5 second intervals) with zero distance throttling for precise rescue targeting.
- **Smart SOS Builder:** A wizard-like interface allowing civilians to construct detailed emergency requests, including medical status, entrapment, and priority scores.
- **Global & Private Mesh Chat:** Switch instantly between a global broadcast channel and encrypted private one-on-one chats.
- **Intelligent Message Caching:** Lightning-fast UI rendering using LRU caches that cleanly separates global messages from private messages.
- **Dark Mode Support:** Modern UI/UX with full support for system Dark Themes.
- **100% Offline Capability:** Operates purely on local hardware (WiFi Direct, Bluetooth, GPS).

---

## 🚁 Rescue Node Hub Mode

In v5.16.0, Rescue Team devices function as autonomous network backbones:
1. **Always-On:** Rescue nodes completely ignore battery-saving protocols.
2. **Network State Pings:** When a rescue node connects, it sends a `NETWORK_STATE_REQUEST` ping to all available devices across the mesh.
3. **Instant Visibility:** Any device (Civilian or Medic) receiving this ping instantly replies with GPS coordinates, role, and battery status, populating the Rescue Map with every connected node simultaneously.

## 🔋 Hardware Extensions: ESP32 WiFi Bridges

To combat smartphone battery drain during extended disasters, the system supports integration with **ESP32 Microcontrollers**.
Placed on trees or rooftops and powered by small 5V solar panels and 18650 batteries, these cheap nodes act as permanent "sky-bridges". They relay messages across large distances without draining mobile batteries.

---

## ⚙️ Technical Specifications

### Network Protocols

#### Transport Layer

| Protocol | Range | Speed | Power | Use Case |
|----------|-------|-------|-------|----------|
| WiFi Direct | ~100m | 100+ Mbps | High | Primary mesh |
| Bluetooth Classic | ~10-30m | 2-3 Mbps | Low | Fallback |

#### Message Format (JSON)

```json
{
  "id": "msg_1673612345000_abc123",
  "senderId": "550e8400-e29b-41d4-a716-446655440000",
  "senderName": "John Doe",
  "receiverId": "ALL",
  "type": "TEXT",
  "content": "Message content here",
  "timestamp": 1673612345000,
  "latitude": 37.7749,
  "longitude": -122.4194,
  "status": "SENT"
}
```

#### Message Types

- `TEXT`: Regular chat message
- `IMAGE`: Highly-compressed Base64 thumbnail images
- `SOS`: Emergency distress signal (Ranked by Priority Score)
- `LOCATION_UPDATE`: Live GPS coordinates broadcast
- `NETWORK_STATE_REQUEST`: Discovery ping for Hubs
- `PUBLIC_KEY`: Encryption key exchange
- `DELIVERY_RECEIPT`: Message delivered confirmation
- `READ_RECEIPT`: Message read confirmation

### Storage

- **Database**: Room (SQLite) - Fully persistent across reboots
- **Tables**: Messages, Members, Locations
- **Encryption**: ECDSA / RSA asymmetric cryptography

---

## 🛠️ Development

### Building the Project

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Clean build
./gradlew clean
```

### Dependencies

- **Google Play Services Nearby**: Core Mesh Networking
- **AndroidX**: AppCompat, Material, Room DB
- **OSMDroid**: Offline mapping
- **Gson**: JSON serialization

### Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

---

## ❓ FAQ

### General Questions

**Q: Does this work without Internet?**  
A: Yes! 100% offline. No Internet, no cell towers, no WiFi routers needed.

**Q: How do Rescue Teams differ from Civilians?**  
A: Rescue Teams act as persistent hubs that don't sleep. They also get a specialized "Rescue Dashboard" that tracks and sorts incoming SOS signals by severity. 

**Q: Does it drain battery fast?**  
A: It requires active hardware (WiFi/Bluetooth/GPS). The app uses a Mesh Survival Mode to suspend discovery at 15% battery for civilians, saving power.

### Technical Questions

**Q: Which phones are supported?**  
A: Android 8.0+ (API 26).

**Q: Do both devices need the app?**  
A: To chat and send SOS, yes. However, Rescue Nodes can broadcast a Captive Portal WiFi network to send basic text alerts to nearby phones that *do not* have the app installed.

**Q: Can iPhone users join?**  
A: No. iOS has strict limitations on P2P networking. Android only for now.

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  <strong>Built for emergencies. Designed for everyone.</strong><br>
  Stay connected when it matters most.
</p>
