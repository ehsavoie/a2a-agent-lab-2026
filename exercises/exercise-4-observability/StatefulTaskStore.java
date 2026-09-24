package dev.devconf.concierge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Demonstration of distributed state management for A2A tasks.
 *
 * In production, replace ConcurrentHashMap with Redis, a database,
 * or another distributed store. The key insight: A2A Tasks have a
 * contextId that groups related conversations — use it as the
 * persistence key for session affinity and state recovery.
 *
 * This is a discussion/demo class for Exercise 5, not a full implementation.
 */
public class StatefulTaskStore {

    private final Map<String, ConversationState> store = new ConcurrentHashMap<>();

    public void saveState(String contextId, ConversationState state) {
        store.put(contextId, state);
    }

    public ConversationState loadState(String contextId) {
        return store.get(contextId);
    }

    public boolean hasState(String contextId) {
        return store.containsKey(contextId);
    }

    public record ConversationState(
            String contextId,
            String lastQuery,
            String lastResponse,
            java.util.List<String> agentsUsed,
            java.time.Instant lastUpdated
    ) {}
}
