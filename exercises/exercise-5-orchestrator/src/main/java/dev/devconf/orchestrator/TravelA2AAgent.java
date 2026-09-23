package dev.devconf.orchestrator;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.service.V;

public interface TravelA2AAgent {

    @A2AClientAgent(
            a2aServerUrl = "http://localhost:9000",
            name = "Travel & Logistics Agent",
            description = "Provides travel tips, transportation options, and logistics information for getting to the venue",
            outputKey = "response"
    )
    String ask(@V("query") String query);
}
