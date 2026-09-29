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
        // Both nodes run the same WAR. The advertised URL is computed from the WildFly
        // socket-binding-port-offset so the AgentCard is always correct on whichever
        // node serves this request (node A: offset 0, node B: offset 1000).
        int portOffset = ConfigProvider.getConfig()
                .getOptionalValue("jboss.socket.binding.port-offset", Integer.class)
                .orElse(0);

        String jsonRpcUrl = "http://localhost:" + (BASE_HTTP_PORT + portOffset);
        List<AgentInterface> interfaces = new ArrayList<>();
        // JSONRPC is always present — it is needed to serve the AgentCard.
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
                .name("Enterprise A2A Agent (DevSphere)")
                .description("Multi-node enterprise agent demonstrating JPA-backed task store and "
                        + "Kafka-replicated queue manager across two WildFly nodes")
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(Collections.singletonList("text"))
                .defaultOutputModes(Collections.singletonList("text"))
                .skills(Collections.singletonList(AgentSkill.builder()
                        .id("hello_world")
                        .name("Hello World")
                        .description("Greets the attendee by name. The task is created on one node "
                                + "and completed on the other, proving cross-node Kafka replication.")
                        .tags(Collections.singletonList("hello world"))
                        .examples(List.of("Hello, Maya!", "Say hello to Maya"))
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
