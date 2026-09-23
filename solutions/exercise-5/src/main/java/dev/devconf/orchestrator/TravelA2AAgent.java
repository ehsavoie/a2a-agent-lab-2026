package dev.devconf.orchestrator;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.service.V;
import dev.langchain4j.agentic.declarative.A2AClientCustomizer;

public interface TravelA2AAgent {

    @A2AClientAgent(
            a2aServerUrl = "http://localhost:9000",
            name = "Travel & Logistics Agent",
            description = "Provides travel tips, transportation options, and logistics information for getting to the venue",
            outputKey = "response"
    )
    String ask(@V("query") String query);

    @A2AClientCustomizer
    static void customizer(ClientBuilder cb) {
        cb.withTransport(RestTransport.class, new RestTransportConfigBuilder());
    }
}
