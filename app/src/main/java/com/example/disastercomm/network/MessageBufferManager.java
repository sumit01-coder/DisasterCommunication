package com.example.disastercomm.network;

import android.util.Log;
import com.example.disastercomm.models.Message;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Advanced Buffer System for Mesh Communication.
 * Handles Priority-based queueing for high Availability.
 */
public class MessageBufferManager {
    private static final String TAG = "MessageBuffer";
    private static final int MAX_BUFFER_SIZE = 1000;

    // Thread-safe Priority Queues
    // Higher priority (SOS=10) comes first
    private final PriorityBlockingQueue<Message> inboundBuffer;
    private final PriorityBlockingQueue<Message> outboundBuffer;

    public MessageBufferManager() {
        this.inboundBuffer = new PriorityBlockingQueue<>(100, com.example.disastercomm.intelligence.PriorityRouter.PRIORITY_COMPARATOR);
        this.outboundBuffer = new PriorityBlockingQueue<>(100, com.example.disastercomm.intelligence.PriorityRouter.PRIORITY_COMPARATOR);
    }

    /**
     * Add message to Inbound Buffer (Incoming from Network)
     */
    public void bufferInbound(Message message) {
        if (inboundBuffer.size() >= MAX_BUFFER_SIZE) {
            // If full, remove lowest priority message to make room
            if (message.priorityScore > 10) {
                Log.w(TAG, "Inbound Buffer Full. Dropping low priority messages.");
                dropLowPriority(inboundBuffer);
            } else {
                return; // Drop this one if it's low priority
            }
        }
        inboundBuffer.add(message);
    }

    /**
     * Add message to Outbound Buffer (Ready to Send)
     */
    public void bufferOutbound(Message message) {
        if (outboundBuffer.size() >= MAX_BUFFER_SIZE) {
            if (message.priorityScore > 10) {
                dropLowPriority(outboundBuffer);
            } else {
                return;
            }
        }
        outboundBuffer.add(message);
    }

    private void dropLowPriority(PriorityBlockingQueue<Message> queue) {
        // Find the lowest priority message and remove it
        Message lowest = null;
        for (Message m : queue) {
            if (lowest == null || m.priorityScore < lowest.priorityScore) {
                lowest = m;
            }
        }
        if (lowest != null && lowest.priorityScore < 90) { // Never drop high priority SOS
            queue.remove(lowest);
        }
    }

    public Message pollInbound() throws InterruptedException {
        return inboundBuffer.poll();
    }

    public Message takeInbound() throws InterruptedException {
        return inboundBuffer.take();
    }

    public Message pollOutbound() throws InterruptedException {
        return outboundBuffer.poll();
    }

    public Message takeOutbound() throws InterruptedException {
        return outboundBuffer.take();
    }

    public int getInboundSize() {
        return inboundBuffer.size();
    }

    public int getOutboundSize() {
        return outboundBuffer.size();
    }
}
