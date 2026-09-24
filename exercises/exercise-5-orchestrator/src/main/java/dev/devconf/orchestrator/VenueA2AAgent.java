package dev.devconf.orchestrator;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.service.V;

public interface VenueA2AAgent {

    @A2AClientAgent(
            a2aServerUrl = "http://localhost:8081",
            name = "Venue & On-Site Operations",
            description = "Provides information about venue layout, room capacities, facilities, and on-site operations",
            outputKey = "response"
    )
    String ask(@V("query") String query);
}
