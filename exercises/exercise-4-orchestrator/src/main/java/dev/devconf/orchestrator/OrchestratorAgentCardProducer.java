package dev.devconf.orchestrator;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class OrchestratorAgentCardProducer {

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
        return AgentCard.builder()
                .name(agentName)
                .description(agentDescription)
                .version(agentVersion)
                .supportedInterfaces(List.of(
                        new AgentInterface(
                                TransportProtocol.JSONRPC.asString(), agentUrl)
                ))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .skills(List.of(
                        AgentSkill.builder()
                                .id("general-conference-assistant")
                                .name("General Conference Assistant")
                                .description("Answer any question about DevConf 2026 by coordinating specialist agents for sessions, venue, travel, and expenses.")
                                .tags(List.of("orchestrator", "conference", "multi-agent"))
                                .examples(List.of(
                                        "My flight was delayed, what sessions can I still catch today?",
                                        "Where should I eat and is Hall B full?",
                                        "How do I get to the venue and log my taxi receipt?"))
                                .build()
                ))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
    }
}
