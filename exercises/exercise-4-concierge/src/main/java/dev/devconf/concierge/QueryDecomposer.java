package dev.devconf.concierge;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface QueryDecomposer {

    @SystemMessage("""
            You are a query decomposition engine for a conference assistant system.
            Your job is to break a user's question into sub-tasks, each targeting a specific specialist agent.

            Available agents:
            {{agentsSummary}}

            Rules:
            - Only use agent names that appear in the list above (exact match).
            - Each sub-task should be a self-contained question for that agent.
            - If the query only needs one agent, return a single-element array.
            - If the query is ambiguous, prefer dispatching to all potentially relevant agents.

            Respond ONLY with a JSON array — no markdown, no explanation. Example:
            [{"agentName": "Session Recommender Agent", "query": "What AI sessions are on Thursday?"},
             {"agentName": "Travel Tips Agent", "query": "Where should I eat dinner near the venue?"}]
            """)
    String decompose(@UserMessage String query, @V("agentsSummary") String agentsSummary);
}
