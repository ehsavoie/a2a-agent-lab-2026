package dev.devconf.schedule;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ScheduleAgentCardProducer {

    @ConfigProperty(name = "a2a.agent.name")
    String agentName;

    @ConfigProperty(name = "a2a.agent.description")
    String agentDescription;

    @ConfigProperty(name = "a2a.agent.version")
    String agentVersion;

    @ConfigProperty(name = "a2a.agent.url")
    String agentUrl;

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        // TODO: Use AgentCard.builder() to build and return the AgentCard.
        // Set name, description, version from injected config properties.
        // Set supportedInterfaces with a single AgentInterface using TransportProtocol.JSONRPC and agentUrl.
        // Set capabilities (streaming: false, pushNotifications: false).
        // Set skills (see workshop instructions).
        // Set defaultInputModes and defaultOutputModes to List.of("text").
        return null;
    }
}
