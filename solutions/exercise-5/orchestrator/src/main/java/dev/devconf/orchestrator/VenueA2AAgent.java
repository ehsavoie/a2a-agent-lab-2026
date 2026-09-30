package dev.devconf.orchestrator;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.service.V;
import dev.langchain4j.agentic.declarative.A2AClientCustomizer;

public interface VenueA2AAgent {

    @A2AClientAgent(
            a2aServerUrl = "http://localhost:8081",
            name = "Venue & On-Site Operations",
            description = "Provides information about venue layout, room capacities, facilities, and on-site operations",
            outputKey = "response"
    )
    String ask(@V("query") String query);

    @A2AClientCustomizer
    static void customizer(ClientBuilder cb) {
        cb.withTransport(RestTransport.class, new RestTransportConfigBuilder());
    }
}
