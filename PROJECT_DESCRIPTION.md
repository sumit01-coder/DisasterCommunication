# 🚨 Disaster Communication Network: Comprehensive Project Description

## 1. Executive Summary
The **Disaster Communication Network** is a highly resilient, offline-first Android application and hardware ecosystem designed to maintain life-saving communication when cellular towers and internet infrastructure collapse. Built for disaster zones, the system leverages decentralized mesh networking, enabling smartphones to communicate directly with one another and relay critical information across a city. 

By combining Android's peer-to-peer radios, offline mapping, and ESP32-S3 hardware hubs equipped with LoRa, the network ensures that civilians, rescue workers, and medical professionals maintain situational awareness and communication in the most extreme conditions.

---

## 2. Core Communication & Networking Architecture

### 2.1 The Mesh Network (Android Nearby Connections API)
The backbone of the app uses Google's **Nearby Connections API** utilizing the `P2P_CLUSTER` strategy. 
- **Multi-Radio Hopping:** The app automatically switches between **Bluetooth, Bluetooth Low Energy (BLE), and Wi-Fi Direct** to find the strongest, longest-range connection to nearby phones.
- **Store-and-Forward Routing:** When a message or map marker is sent, it is stored in a local SQLite database (Room). When a user walks near another user, the devices automatically handshake in the background and securely sync their databases. 
- **Viral Propagation:** If User A meets User B, and later User B meets User C, User C receives User A's data. This creates a "sneakernet" where data physically travels with people moving around the disaster zone.

### 2.2 ESP32-S3 & LoRa Hardware Integration
Because smartphone Wi-Fi Direct is limited to ~100 meters, the project supports hardware augmentation via ESP32-S3 microcontrollers.
- **Wi-Fi Access Point & TCP Relay:** The ESP32-S3 acts as a standalone Wi-Fi router. Multiple smartphones can connect to it simultaneously.
- **Local TCP Manager:** The Android app runs a background `LocalTcpManager` that binds to port `8080`. When connected to the ESP32, the app blasts JSON payloads to the ESP32.
- **LoRa Bridge:** The ESP32-S3 is programmed to take incoming TCP packets and transmit them over a **LoRa (Long Range) Radio module**. This blasts the mesh data miles away to other towers, bridging local smartphone clusters over massive distances.

---

## 3. Offline Mapping & Routing (OSMDroid)

### 3.1 Fully Offline Map Engine
The app completely strips out Google Maps (which requires an internet connection) and replaces it with **OSMDroid**.
- **Tile Caching:** Map tiles are downloaded and aggressively cached on the device memory. Even with zero internet, the user can navigate their city.
- **Battery-Saving Dark Mode:** A custom color-matrix filter aggressively inverts map colors, drastically reducing battery drain on AMOLED screens—crucial when power grids are down.

### 3.2 Crowdsourced Hazard Mapping
Civilians and rescue workers can act as scouts. By **long-pressing** anywhere on the map, users can drop emergency markers:
- 🌊 Flood / High Water
- 🔥 Fire / Smoke
- 🛡️ Safe Zone
- 🚧 Blocked Road
Once a marker is dropped, it is packaged as a JSON payload and blasted across the mesh network. As other phones receive this payload, the hazard instantly appears on their screens.

### 3.3 Dynamic Routing & Emergency Paths
Users can tap on any hospital or user marker and press **"GET ROUTE"**.
- **OSRM Road Routing:** If a sliver of internet is available, the app queries the Open Source Routing Machine (OSRM) to draw a thick, solid blue path that perfectly follows streets and roads.
- **Offline Emergency Fallback:** If completely offline, the app dynamically falls back to drawing a highly visible, thick dashed red line straight from the user to the destination, acting as an "as-the-crow-flies" emergency compass.

---

## 4. User Interface & Experience

### 4.1 Accessibility & Ethics-First Design
The UI is strictly designed for high-stress environments:
- **Color Palette:** Utilizes a calming, accessible "Messenger Blue" and "Success Green" palette. Harsh, panic-inducing reds are strictly reserved for critical errors or offline fallbacks.
- **Bottom Sheets:** All interactions (Layers, Marker Details, Reporting) are handled via smooth, sliding Bottom Sheets, keeping the map visible at all times.

### 4.2 Member Tracking
The app tracks and displays network participants on the map with specific roles:
- **Civilians**
- **Rescue Workers**
- **Medical Professionals**
- **Community Volunteers**
Real-time distance, signal strength, connection type (Wi-Fi/Bluetooth), and connection quality percentages are displayed when tapping a user.

---

## 5. Power & Resource Management

Operating in a disaster means electricity is a luxury. The app is heavily optimized for battery life:
- **Radio Duty-Cycling:** The mesh network radios do not stay on 100% of the time. They pulse on and off to conserve battery while maintaining network integrity.
- **OLED Optimization:** The dark theme is true black `#000000`, actually turning off pixels on modern smartphone screens.
- **Executor Threads:** All heavy database operations, network discovery, and routing calculations are pushed to background `ExecutorService` threads, ensuring the UI never lags or freezes.

---

## 6. Technical Stack
- **Language:** Java (Android SDK) & C++ (ESP32 Arduino Firmware)
- **Minimum SDK:** API 24 (Android 7.0 - ensuring compatibility with older, cheaper devices)
- **Networking:** Google Play Services Nearby Connections, `java.net.Socket`, HTTPUrlConnection
- **Database:** SQLite via Android Room Persistence Library
- **Mapping:** OSMDroid (OpenStreetMap)
- **Routing:** OSRM (Open Source Routing Machine) JSON API
- **Build System:** Gradle (Version 5.0.0 Release)
