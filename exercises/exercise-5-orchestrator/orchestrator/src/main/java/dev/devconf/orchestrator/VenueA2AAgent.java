package dev.devconf.orchestrator;

import dev.langchain4j.agentic.a2a.A2AContextId;
import dev.langchain4j.agentic.a2a.A2ATaskId;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;

public interface VenueA2AAgent {

    @A2AClientAgent(
            a2aServerUrl = "http://localhost:8080/.well-known/venue/agent-card.json",
            name = "Venue & On-Site Operations",
            description = "Provides information about venue layout, room capacities, facilities, and on-site operations",
            outputKey = "response"
    )
    ResultWithAgenticScope<String> ask(@V("query") String query, @A2AContextId @V("contextId") String contextId, @A2ATaskId @V("taskId") String taskId);
}
