package dev.devconf.enterprise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class EnterpriseAgentCardProducer {

    private static final int BASE_HTTP_PORT = 8080;
    private static final int BASE_GRPC_PORT = 9555;

    @Produces
    @PublicAgentCard
    public AgentCard createAgentCard() {
        // Both nodes deploy the same WAR. The advertised URL is computed from the WildFly
        // socket-binding-port-offset so the AgentCard is always correct on whichever
        // node serves this request (node A: offset 0, node B: offset 1000).
        int portOffset = ConfigProvider.getConfig()
                .getOptionalValue("jboss.socket.binding.port-offset", Integer.class)
                .orElse(0);

        String jsonRpcUrl = "http://localhost:" + (BASE_HTTP_PORT + portOffset);
        List<AgentInterface> interfaces = new ArrayList<>();
        interfaces.add(new AgentInterface(TransportProtocol.JSONRPC.asString(), jsonRpcUrl));
        if (isRest()) {
            interfaces.add(new AgentInterface(TransportProtocol.HTTP_JSON.asString(), jsonRpcUrl));
        }
        if (isGrpcEnabled()) {
            interfaces.add(new AgentInterface(
                    TransportProtocol.GRPC.asString(),
                    "localhost:" + (BASE_GRPC_PORT + portOffset)));
        }

        return AgentCard.builder()
                .name("Conference Feedback Agent (DevSphere)")
                .description("Collects attendee feedback for conference sessions and provides "
                        + "per-talk or global summaries to speakers. Backed by a shared PostgreSQL "
                        + "database and Kafka-replicated queue across two WildFly nodes.")
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(List.of(
                        AgentSkill.builder()
                                .id("submit-feedback")
                                .name("Submit Session Feedback")
                                .description("Record an attendee's rating and comment for a speaker's session.")
                                .tags(List.of("feedback", "rating", "conference"))
                                .examples(List.of(
                                        "Feedback for Mario Fusco, session: Building Production-Ready Agentic Systems with LangChain4j and Quarkus, rating: 5, absolutely loved the live coding demo!",
                                        "Feedback for Guillaume Laforge, session: Choose your own adventure in agentic design patterns, rating: 5, best format of the conference!"))
                                .build(),
                        AgentSkill.builder()
                                .id("feedback-summary")
                                .name("Feedback Summary")
                                .description("Return aggregated feedback for a speaker, optionally filtered to a specific session.")
                                .tags(List.of("feedback", "summary", "speaker"))
                                .examples(List.of(
                                        "Summary for Mario Fusco",
                                        "What feedback did Mario Fusco receive for his LangChain4j talk?"))
                                .build()))
                .supportedInterfaces(interfaces)
                .build();
    }

    private boolean isGrpcEnabled() {
        try {
            Class.forName("org.wildfly.a2a.jakarta.grpc.GrpcBeanInitializer");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isRest() {
        try {
            Class.forName("org.wildfly.a2a.jakarta.rest.WildFlyRestTransportMetadata");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
