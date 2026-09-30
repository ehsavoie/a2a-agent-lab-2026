package dev.devconf.expense;

import java.util.ArrayList;
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
public class ExpenseAgentCardProducer {

    private static final int BASE_HTTP_PORT = 8080;
    private static final int BASE_GRPC_PORT = 9555;

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        // TODO: Build and return an AgentCard using AgentCard.builder().
        //
        // 1. Read the port offset:
        //      int portOffset = ConfigProvider.getConfig()
        //          .getOptionalValue("jboss.socket.binding.port-offset", Integer.class).orElse(0);
        //      String jsonRpcUrl = "http://localhost:" + (BASE_HTTP_PORT + portOffset);
        //
        // 2. Build the interfaces list (List<AgentInterface>):
        //      - Always add AgentInterface(TransportProtocol.JSONRPC.asString(), jsonRpcUrl)
        //      - If isRest(): add AgentInterface(TransportProtocol.HTTP_JSON.asString(), jsonRpcUrl)
        //      - If isGrpcEnabled(): add AgentInterface(TransportProtocol.GRPC.asString(), "localhost:" + (BASE_GRPC_PORT + portOffset))
        //
        // 3. Call AgentCard.builder() with name, description, version, supportedInterfaces(interfaces),
        //    capabilities (streaming: true), skills (see workshop), defaultInputModes/OutputModes, then .build()
        return null;
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
