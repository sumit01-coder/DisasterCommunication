package com.example.disastercomm.network;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Automatically discovers other phones on the same Wi-Fi Network/Router
 * using UDP Broadcasts.
 */
public class UdpDiscoveryManager {
    private static final String TAG = "UdpDiscovery";
    private static final int UDP_PORT = 8889;
    private static final long BROADCAST_INTERVAL = 5000; // 5 seconds

    private final Context context;
    private final String username;
    private final String deviceId;
    private final LocalTcpManager localTcpManager;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private boolean isRunning = false;
    private DatagramSocket socket;

    public UdpDiscoveryManager(Context context, String username, String deviceId, LocalTcpManager tcpManager) {
        this.context = context;
        this.username = username;
        this.deviceId = deviceId;
        this.localTcpManager = tcpManager;
    }

    public synchronized void start() {
        if (isRunning) return;
        isRunning = true;
        executor.execute(this::listenForBroadcasts);
        executor.execute(this::sendBroadcastLoop);
        Log.d(TAG, "UDP Discovery started");
    }

    public synchronized void stop() {
        isRunning = false;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        executor.shutdownNow();
        Log.d(TAG, "UDP Discovery stopped");
    }

    private void listenForBroadcasts() {
        try {
            socket = new DatagramSocket(UDP_PORT);
            socket.setBroadcast(true);
            byte[] buffer = new byte[1024];

            while (isRunning) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String senderIp = packet.getAddress().getHostAddress();
                String myIp = getLocalIpAddress();

                // Don't connect to self
                if (senderIp != null && !senderIp.equals(myIp)) {
                    String data = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                    if (data.startsWith("DISCOVER_PEER:")) {
                        Log.d(TAG, "Discovered peer via UDP: " + data + " at " + senderIp);
                        localTcpManager.connectToPeer(senderIp);
                    }
                }
            }
        } catch (IOException e) {
            if (isRunning) {
                Log.e(TAG, "UDP Listener error", e);
            }
        }
    }

    private void sendBroadcastLoop() {
        while (isRunning) {
            try {
                InetAddress broadcastAddress = getBroadcastAddress();
                if (broadcastAddress != null) {
                    String message = "DISCOVER_PEER:" + username + "|" + deviceId;
                    byte[] data = message.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddress, UDP_PORT);
                    
                    DatagramSocket sendSocket = new DatagramSocket();
                    sendSocket.setBroadcast(true);
                    sendSocket.send(packet);
                    sendSocket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Failed to send UDP broadcast", e);
            }

            try {
                Thread.sleep(BROADCAST_INTERVAL);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp = wifi.getDhcpInfo();
        if (dhcp == null) return InetAddress.getByName("255.255.255.255");

        int broadcast = (dhcp.ipAddress & dhcp.netmask) | (~dhcp.netmask);
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
        return InetAddress.getByAddress(quads);
    }

    private String getLocalIpAddress() {
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ipInt = wifi.getConnectionInfo().getIpAddress();
        return String.format("%d.%d.%d.%d", (ipInt & 0xff), (ipInt >> 8 & 0xff), (ipInt >> 16 & 0xff), (ipInt >> 24 & 0xff));
    }
}
