package dev.devconf.orchestrator;

import dev.langchain4j.agentic.a2a.A2AContextId;
import dev.langchain4j.agentic.a2a.A2ATaskId;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;

public interface TravelA2AAgent {

    // TODO: Annotate this method with @A2AClientAgent to connect to the Travel tenant on the Concierge.
    //       - a2aServerUrl: base URL of the Concierge (http://localhost:8080/)
    //       - tenant: the tenant name registered in the Concierge for travel
    //       - name: a short human-readable name for this agent
    //       - description: what queries this agent can handle (travel, transport, logistics)
    //       - outputKey: the agentic scope key for this agent's response ("travel-response")
    ResultWithAgenticScope<String> ask(@V("query") String query, @A2AContextId @V("contextId") String contextId, @A2ATaskId @V("taskId") String taskId);
}
