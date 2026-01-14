# Technical Architecture Guide

## System Architecture Overview

This document provides in-depth technical details about the DisasterComm system architecture, network protocols, and implementation details.

---

## 1. Network Layer Architecture

### 1.1 Transport Layer

DisasterComm implements a **dual-transport mesh network** using two protocols simultaneously:

#### WiFi Direct (Primary Transport)

**Implementation**: Google Nearby Connections API  
**Class**: `MeshNetworkManager.java`

```java
// Strategy for mesh networking
private static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

// Allows multiple simultaneous connections
// Optimized for mesh topology
```

**Connection Flow**:

1. **Advertising**
   ```java
   connectionsClient.startAdvertising(
       username + "__" + deviceId,  // Endpoint name
       SERVICE_ID,                   // Service identifier
       connectionLifecycleCallback,  // Connection events
       advertisingOptions            // WiFi, BLE options
   );
   ```

2. **Discovery**
   ```java
   connectionsClient.startDiscovery(
       SERVICE_ID,
       endpointDiscoveryCallback,
       discoveryOptions
   );
   ```

3. **Connection Establishment**
   ```java
   connectionsClient.requestConnection(
       localDeviceName,
       endpointId,
       connectionLifecycleCallback
   );
   ```

4. **Payload Transfer**
   ```java
   Payload payload = Payload.fromBytes(messageBytes);
   connectionsClient.sendPayload(endpointId, payload);
   ```

#### Bluetooth Classic (Fallback Transport)

**Implementation**: Android Bluetooth RFCOMM  
**Class**: `BluetoothConnectionManager.java`

**Connection Types**:

1. **Server Socket** (Accept Incoming)
   ```java
   BluetoothServerSocket serverSocket = 
       bluetoothAdapter.listenUsingRfcommWithServiceRecord(
           SERVICE_NAME, 
           SERVICE_UUID
       );
   
   BluetoothSocket socket = serverSocket.accept();
   ```

2. **Client Socket** (Initiate Outgoing)
   ```java
   BluetoothSocket socket = 
       device.createRfcommSocketToServiceRecord(SERVICE_UUID);
   
   socket.connect();
   ```

**Data Transfer**:
```java
OutputStream out = socket.getOutputStream();
InputStream in = socket.getInputStream();

// Write
out.write(messageBytes);

// Read
byte[] buffer = new byte[1024];
int bytes = in.read(buffer);
```

### 1.2 Message Layer

**Class**: `PacketHandler.java`

#### Message Serialization

**Format**: JSON (via Gson library)

```java
public class Message {
    public String id;              // Unique message ID
    public String senderId;        // Sender device UUID
    public String senderName;      // Human-readable name
    public String receiverId;      // "ALL" or specific UUID
    public Type type;              // TEXT, SOS, LOCATION_UPDATE, etc.
    public String content;         // Message payload
    public long timestamp;         // Unix timestamp (ms)
    public Double latitude;        // GPS latitude (optional)
    public Double longitude;       // GPS longitude (optional)
    public Status status;          // SENT, DELIVERED, READ
    public String receiptFor;      // Message ID for receipts
}
```

**Serialization**:
```java
Gson gson = new Gson();
String json = gson.toJson(message);
byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
```

**Deserialization**:
```java
String json = new String(bytes, StandardCharsets.UTF_8);
Message message = gson.fromJson(json, Message.class);
```

#### Message Routing

**Broadcast Messages** (Global Chat, SOS):
```java
public void sendMessage(Message message) {
    byte[] payload = serializeMessage(message);
    
    // Send via WiFi Direct (all connected endpoints)
    meshNetworkManager.broadcastPayload(payload);
    
    // Send via Bluetooth (all connected devices)
    bluetoothManager.broadcastData(payload);
}
```

**Unicast Messages** (Private Chat):
```java
public void sendPrivateMessage(Message message, String recipientId) {
    byte[] payload = serializeMessage(message);
    
    // Route to specific device
    String endpointId = findEndpointForDevice(recipientId);
    
    if (endpointId != null) {
        meshNetworkManager.sendPayload(endpointId, payload);
    } else {
        // Not directly connected, relay needed
        broadcastForRelay(payload);
    }
}
```

#### Message Deduplication

**Problem**: In mesh networks, same message can arrive via multiple paths

**Solution**: Message ID tracking with TTL

```java
private Set<String> seenMessageIds = new HashSet<>();
private Map<String, Long> messageTimestamps = new HashMap<>();

private static final long MESSAGE_TTL = 5 * 60 * 1000; // 5 minutes

public boolean isMessageNew(Message message) {
    // Check if already seen
    if (seenMessageIds.contains(message.id)) {
        return false;
    }
    
    // Add to seen set
    seenMessageIds.add(message.id);
    messageTimestamps.put(message.id, System.currentTimeMillis());
    
    // Cleanup old entries
    cleanupOldMessages();
    
    return true;
}

private void cleanupOldMessages() {
    long now = System.currentTimeMillis();
    messageTimestamps.entrySet().removeIf(entry -> 
        now - entry.getValue() > MESSAGE_TTL
    );
}
```

---

## 2. Data Persistence

### 2.1 Database Schema

**Implementation**: Room (SQLite ORM)  
**Class**: `AppDatabase.java`

#### Message Table

```sql
CREATE TABLE messages (
    id TEXT PRIMARY KEY,
    senderId TEXT NOT NULL,
    senderName TEXT,
    receiverId TEXT,
    type TEXT NOT NULL,
    content TEXT,
    timestamp INTEGER NOT NULL,
    latitude REAL,
    longitude REAL,
    status TEXT,
    receiptFor TEXT
);

CREATE INDEX idx_timestamp ON messages(timestamp DESC);
CREATE INDEX idx_sender ON messages(senderId);
```

**DAO Operations**:

```java
@Dao
public interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    List<Message> getAllMessages();
    
    @Query("SELECT * FROM messages WHERE receiverId = 'ALL' ORDER BY timestamp DESC")
    List<Message> getGlobalMessages();
    
    @Query("SELECT * FROM messages WHERE (senderId = :userId OR receiverId = :userId) AND receiverId != 'ALL' ORDER BY timestamp")
    List<Message> getPrivateMessages(String userId);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Message message);
    
    @Update
    void update(Message message);
    
    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    void updateStatus(String messageId, Message.Status status);
}
```

---

## 3. Location Services

### 3.1 LocationHelper Implementation

**Class**: `LocationHelper.java`

#### Permission Handling

```java
private static final String[] REQUIRED_PERMISSIONS = {
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
};

public boolean hasLocationPermissions() {
    return ContextCompat.checkSelfPermission(context, 
        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
}
```

#### Location Retrieval

```java
public void getCurrentLocation(LocationCallback callback) {
    LocationManager locationManager = 
        (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    
    Criteria criteria = new Criteria();
    criteria.setAccuracy(Criteria.ACCURACY_FINE);
    criteria.setPowerRequirement(Criteria.POWER_MEDIUM);
    
    locationManager.requestSingleUpdate(criteria, new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            callback.onLocation(
                location.getLatitude(), 
                location.getLongitude()
            );
        }
    }, null);
}
```

#### Distance Calculation

**Haversine Formula** for great-circle distance:

```java
public static double calculateDistance(
    double lat1, double lon1, 
    double lat2, double lon2
) {
    final int R = 6371; // Earth's radius in km
    
    double latDistance = Math.toRadians(lat2 - lat1);
    double lonDistance = Math.toRadians(lon2 - lon1);
    
    double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
    
    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    
    return R * c; // Distance in km
}
```

---

## 4. Notification System

### 4.1 Notification Channels

**Android 8.0+ (API 26) Requirement**

```java
private void createNotificationChannels() {
    NotificationManager manager = 
        context.getSystemService(NotificationManager.class);
    
    // High Priority: Messages & SOS
    NotificationChannel messagesChannel = new NotificationChannel(
        "channel_messages",
        "Messages",
        NotificationManager.IMPORTANCE_HIGH
    );
    messagesChannel.enableVibration(true);
    messagesChannel.setSound(defaultSoundUri, audioAttributes);
    
    // Default Priority: Connections
    NotificationChannel connectionsChannel = new NotificationChannel(
        "channel_connections",
        "Device Connections",
        NotificationManager.IMPORTANCE_DEFAULT
    );
    
    // Low Priority: Network & System
    NotificationChannel networkChannel = new NotificationChannel(
        "channel_network",
        "Network Status",
        NotificationManager.IMPORTANCE_LOW
    );
    
    manager.createNotificationChannel(messagesChannel);
    manager.createNotificationChannel(connectionsChannel);
    manager.createNotificationChannel(networkChannel);
}
```

### 4.2 Notification Types

#### Message Notification

```java
public void showMessageNotification(String senderName, String message, String senderId) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
        .setSmallIcon(R.drawable.ic_app_logo)
        .setContentTitle(senderName)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(createPendingIntent(senderId));
    
    NotificationManagerCompat.from(context)
        .notify(senderId.hashCode(), builder.build());
}
```

#### SOS Notification

```java
public void showSosNotification(String senderName, String message) {
    NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID_SOS)
        .setSmallIcon(android.R.drawable.ic_dialog_alert)
        .setContentTitle("🚨 SOS: " + senderName)
        .setContentText(message)
        .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .setVibrate(new long[]{0, 1000, 500, 1000})
        .setAutoCancel(true);
    
    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SOS, builder.build());
}
```

---

## 5. Security Architecture

### 5.1 Current Security Measures

#### Transport Security

1. **WiFi Direct**
   - WPA2 encryption on direct connections
   - Secure handshake during connection establishment

2. **Bluetooth**
   - RFCOMM encryption
   - Optional device pairing

#### Data Privacy

```java
public class Message {
    // No personally identifiable information stored
    // Only device UUIDs and chosen usernames
    
    @Ignore
    private transient String deviceHardwareId; // Not serialized
}
```

### 5.2 Planned Enhancements

#### End-to-End Encryption

**Algorithm**: RSA + AES hybrid

```java
// Key generation (planned)
public void generateKeyPair() {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    
    privateKey = keyPair.getPrivate();
    publicKey = keyPair.getPublic();
}

// Message encryption (planned)
public byte[] encryptMessage(String message, PublicKey recipientPublicKey) {
    // Generate AES session key
    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
    keyGen.init(256);
    SecretKey sessionKey = keyGen.generateKey();
    
    // Encrypt message with AES
    Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
    aesCipher.init(Cipher.ENCRYPT_MODE, sessionKey);
    byte[] encryptedMessage = aesCipher.doFinal(message.getBytes());
    
    // Encrypt session key with RSA
    Cipher rsaCipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    rsaCipher.init(Cipher.ENCRYPT_MODE, recipientPublicKey);
    byte[] encryptedKey = rsaCipher.doFinal(sessionKey.getEncoded());
    
    // Combine encrypted key + encrypted message
    return combineKeyAndMessage(encryptedKey, encryptedMessage);
}
```

---

## 6. Performance Optimization

### 6.1 Connection Management

#### Connection Pooling

```java
// Reuse existing connections
private Map<String, ConnectionInfo> activeConnections = new ConcurrentHashMap<>();

public void sendMessage(String deviceId, byte[] data) {
    ConnectionInfo connection = activeConnections.get(deviceId);
    
    if (connection != null && connection.isActive()) {
        // Reuse existing connection
        connection.send(data);
    } else {
        // Establish new connection
        establishConnection(deviceId, data);
    }
}
```

#### Adaptive Discovery

```java
// Reduce scan frequency when many devices connected
private int getDiscoveryInterval() {
    int connectedCount = activeConnections.size();
    
    if (connectedCount >= 5) {
        return 60000; // 1 minute
    } else if (connectedCount >= 2) {
        return 30000; // 30 seconds
    } else {
        return 10000; // 10 seconds (aggressive)
    }
}
```

### 6.2 Battery Optimization

#### Background Service Management

```java
// Use JobScheduler for periodic tasks
public class NetworkMaintenanceJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        // Cleanup old connections
        // Remove stale devices
        // Optimize routing tables
        
        return false; // Job complete
    }
}
```

#### Doze Mode Compatibility

```java
// Request battery optimization exemption for critical features
public void requestBatteryOptimizationExemption() {
    Intent intent = new Intent();
    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
    intent.setData(Uri.parse("package:" + getPackageName()));
    startActivity(intent);
}
```

---

## 7. Error Handling & Recovery

### 7.1 Connection Recovery

```java
private void handleConnectionLost(String endpointId) {
    // Remove from active connections
    activeConnections.remove(endpointId);
    
    // Notify UI
    callback.onDeviceDisconnected(endpointId);
    
    // Attempt reconnection (with backoff)
    scheduleReconnection(endpointId);
}

private void scheduleReconnection(String endpointId) {
    int attempt = reconnectionAttempts.getOrDefault(endpointId, 0);
    long delay = Math.min(1000 * (1 << attempt), 60000); // Exponential backoff, max 60s
    
    handler.postDelayed(() -> {
        attemptReconnection(endpointId);
        reconnectionAttempts.put(endpointId, attempt + 1);
    }, delay);
}
```

### 7.2 Message Delivery Guarantees

```java
private void sendWithRetry(String endpointId, byte[] data, int maxRetries) {
    new Thread(() -> {
        for (int i = 0; i < maxRetries; i++) {
            try {
                meshNetworkManager.sendPayload(endpointId, data);
                return; // Success
            } catch (Exception e) {
                Log.e(TAG, "Send attempt " + (i + 1) + " failed", e);
                
                if (i < maxRetries - 1) {
                    Thread.sleep(1000); // Wait before retry
                }
            }
        }
        
        // All retries failed
        handleSendFailure(endpointId, data);
    }).start();
}
```

---

## 8. Testing Strategy

### 8.1 Unit Tests

```java
@Test
public void testMessageSerialization() {
    Message message = new Message(
        "device123", 
        "TestUser", 
        Message.Type.TEXT, 
        "Hello"
    );
    
    String json = gson.toJson(message);
    Message deserialized = gson.fromJson(json, Message.class);
    
    assertEquals(message.content, deserialized.content);
    assertEquals(message.senderId, deserialized.senderId);
}

@Test
public void testDistanceCalculation() {
    // San Francisco to Los Angeles
    double distance = LocationHelper.calculateDistance(
        37.7749, -122.4194,  // SF
        34.0522, -118.2437   // LA
    );
    
    assertTrue(distance > 550 && distance < 560); // ~559 km
}
```

### 8.2 Integration Tests

```java
@Test
public void testMeshRelay() {
    // Setup: Device A ← Device B ← Device C
    // Test: Message from A should reach C through B
    
    Message message = new Message("deviceA", "UserA", Message.Type.TEXT, "Test");
    
    // A sends message
    deviceA.sendMessage(message);
    
    // B receives and relays
    verify(deviceB, timeout(1000)).handlePayload(any());
    
    // C receives
    verify(deviceC, timeout(2000)).onMessageReceived(message);
}
```

---

## 9. Monitoring & Debugging

### 9.1 Logging Strategy

```java
// Use Android's Log levels appropriately
Log.v(TAG, "Verbose: Detailed flow information");
Log.d(TAG, "Debug: Developer information");
Log.i(TAG, "Info: General information");
Log.w(TAG, "Warning: Potential issues");
Log.e(TAG, "Error: Actual errors", exception);
```

### 9.2 Performance Metrics

```java
public class NetworkMetrics {
    private long messagesGent = 0;
    private long messagesReceived = 0;
    private long bytesTransferred = 0;
    private long connectionUptime = 0;
    
    public void recordMessageSent(int size) {
        messagesSent++;
        bytesTransferred += size;
    }
    
    public double getAverageLatency() {
        // Calculate from delivery receipts
        return averageLatencyMs;
    }
    
    public void logMetrics() {
        Log.i(TAG, "=== Network Metrics ===");
        Log.i(TAG, "Messages sent: " + messagesSent);
        Log.i(TAG, "Messages received: " + messagesReceived);
        Log.i(TAG, "Bytes transferred: " + bytesTransferred);
        Log.i(TAG, "Average latency: " + getAverageLatency() + "ms");
    }
}
```

---

## 10. Future Enhancements

### Planned Features

1. **Enhanced Routing**
   - Dijkstra's algorithm for shortest path
   - Dynamic routing table updates
   - Load balancing across multiple paths

2. **Voice Communication**
   - Low-latency audio streaming
   - Codec: Opus for efficiency
   - Push-to-talk interface

3. **File Sharing**
   - Image compression before transfer
   - Chunked transfer with resume support
   - Progress indicators

4. **Offline Maps**
   - Predownload map tiles
   - Vector tiles for smaller size
   - Points of interest (hospitals, shelters)

5. **Group Management**
   - Create persistent groups
   - Group-specific channels
   - Admin controls

---

## Conclusion

This technical architecture enables resilient, decentralized communication in disaster scenarios. The dual-transport mesh design ensures redundancy, while the modular architecture allows for future enhancements without major refactoring.

For implementation questions or contributions, see the main [README](README.md).
