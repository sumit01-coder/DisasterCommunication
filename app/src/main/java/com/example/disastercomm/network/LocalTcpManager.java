package com.example.disastercomm.network;

import android.util.Log;
import com.example.disastercomm.models.Message;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalTcpManager {
    private static final String TAG = "LocalTcpManager";
    private static final int PORT = 8888;

    private final String username;
    private final ConnectionPoolManager poolManager;
    private final ExecutorService serverExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();
    private final Map<String, SocketInfo> activeConnections = new ConcurrentHashMap<>();

    private ServerSocket serverSocket;
    private boolean isRunning = false;
    private PacketHandler packetHandler;

    public interface PacketReceiver {
        void onPacketReceived(String endpointId, byte[] data);
    }

    private PacketReceiver receiver;

    public static class SocketInfo {
        public final Socket socket;
        public final OutputStream outputStream;
        public String peerName;

        public SocketInfo(Socket socket) throws IOException {
            this.socket = socket;
            this.outputStream = socket.getOutputStream();
            this.peerName = "Unknown TCP Peer";
        }
    }

    public LocalTcpManager(String username, ConnectionPoolManager poolManager, PacketReceiver receiver) {
        this.username = username;
        this.poolManager = poolManager;
        this.receiver = receiver;
    }

    public void setPacketHandler(PacketHandler handler) {
        this.packetHandler = handler;
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;
        serverExecutor.execute(this::runServer);
        Log.d(TAG, "Local TCP Server started on port " + PORT);
    }

    public synchronized void stop() {
        if (!isRunning) return;
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to close server socket", e);
        }

        for (Map.Entry<String, SocketInfo> entry : activeConnections.entrySet()) {
            closeSocket(entry.getKey(), entry.getValue().socket);
        }
        activeConnections.clear();

        serverExecutor.shutdownNow();
        clientExecutor.shutdownNow();
        Log.d(TAG, "Local TCP Server stopped");
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(PORT);
            while (isRunning) {
                Socket socket = serverSocket.accept();
                String ipAddress = socket.getInetAddress().getHostAddress();
                Log.d(TAG, "Accepted TCP connection from: " + ipAddress);
                handleNewConnection(ipAddress, socket);
            }
        } catch (IOException e) {
            if (isRunning) {
                Log.e(TAG, "Server socket error", e);
            }
        }
    }

    public void connectToPeer(String ipAddress) {
        clientExecutor.execute(() -> {
            if (activeConnections.containsKey(ipAddress)) {
                return;
            }
            try {
                Log.d(TAG, "Connecting to TCP peer: " + ipAddress + ":" + PORT);
                Socket socket = new Socket(ipAddress, PORT);
                handleNewConnection(ipAddress, socket);
            } catch (IOException e) {
                Log.e(TAG, "Failed to connect to peer " + ipAddress, e);
            }
        });
    }

    private void handleNewConnection(String ipAddress, Socket socket) {
        clientExecutor.execute(() -> {
            try {
                SocketInfo info = new SocketInfo(socket);
                activeConnections.put(ipAddress, info);

                // 1. Send Handshake
                String handshake = "HELLO:" + username + "\n";
                info.outputStream.write(handshake.getBytes(StandardCharsets.UTF_8));
                info.outputStream.flush();

                // 2. Read Loop
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("HELLO:")) {
                        String peerName = line.substring(6).trim();
                        info.peerName = peerName;
                        if (poolManager != null) {
                            poolManager.addConnection(ipAddress, peerName, ConnectionPoolManager.TransportType.TCP_SOCKET);
                        }
                        Log.d(TAG, "Handshake completed with " + peerName + " (" + ipAddress + ")");
                    } else {
                        // Deliver payload
                        byte[] payloadBytes = line.getBytes(StandardCharsets.UTF_8);
                        if (receiver != null) {
                            receiver.onPacketReceived("TCP_" + ipAddress, payloadBytes);
                        }
                    }
                }
            } catch (IOException e) {
                Log.d(TAG, "TCP Connection lost with " + ipAddress + ": " + e.getMessage());
            } finally {
                closeSocket(ipAddress, socket);
            }
        });
    }

    private void closeSocket(String ipAddress, Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            // Ignore
        }
        activeConnections.remove(ipAddress);
        if (poolManager != null) {
            poolManager.removeConnection(ipAddress);
        }
        Log.d(TAG, "Closed TCP connection to: " + ipAddress);
    }

    public void broadcastData(byte[] data, String excludeEndpointId) {
        String cleanExcludeIp = null;
        if (excludeEndpointId != null && excludeEndpointId.startsWith("TCP_")) {
            cleanExcludeIp = excludeEndpointId.substring(4);
        }

        String messageStr = new String(data, StandardCharsets.UTF_8).trim() + "\n";
        byte[] payloadBytes = messageStr.getBytes(StandardCharsets.UTF_8);

        for (Map.Entry<String, SocketInfo> entry : activeConnections.entrySet()) {
            String ipAddress = entry.getKey();
            if (cleanExcludeIp != null && cleanExcludeIp.equals(ipAddress)) {
                continue; // Skip sender
            }

            clientExecutor.execute(() -> {
                try {
                    SocketInfo info = entry.getValue();
                    info.outputStream.write(payloadBytes);
                    info.outputStream.flush();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to send data to TCP peer " + ipAddress, e);
                    closeSocket(ipAddress, entry.getValue().socket);
                }
            });
        }
    }

    public int getActiveConnectionCount() {
        return activeConnections.size();
    }
}
