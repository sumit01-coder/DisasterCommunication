package com.example.disastercomm.intelligence;

import com.example.disastercomm.models.Message;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class PriorityRouter {
    
    /**
     * Comparator for PriorityQueue that prioritizes messages based on priorityScore.
     * Higher score = higher priority.
     */
    public static final Comparator<Message> PRIORITY_COMPARATOR = new Comparator<Message>() {
        @Override
        public int compare(Message m1, Message m2) {
            // Compare by priorityScore (descending)
            if (m1.priorityScore != m2.priorityScore) {
                return Integer.compare(m2.priorityScore, m1.priorityScore);
            }
            // If scores are equal, legacy priority field acts as a tiebreaker
            if (m1.priority != m2.priority) {
                return Integer.compare(m2.priority, m1.priority);
            }
            // If still equal, prioritize older messages to avoid starvation
            return Long.compare(m1.timestamp, m2.timestamp);
        }
    };

    /**
     * Sorts a list of messages in-place based on priority.
     * Use this before processing a batch of queued messages.
     */
    public static void sortMessageQueue(List<Message> queue) {
        queue.sort(PRIORITY_COMPARATOR);
    }
}
