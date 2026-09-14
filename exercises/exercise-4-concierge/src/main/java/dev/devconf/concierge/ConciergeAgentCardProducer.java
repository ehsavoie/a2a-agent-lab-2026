package dev.devconf.concierge;

import java.util.List;

import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.a2aproject.sdk.server.PublicAgentCard;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ConciergeAgentCardProducer {

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
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), agentUrl)
                ))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .skills(List.of(
                        AgentSkill.builder()
                                .id("general-conference-assistant")
                                .name("General Conference Assistant")
                                .description("Answers complex, multi-domain questions about DevConf 2026 by coordinating specialist agents for sessions, travel, dining, and more.")
                                .tags(List.of("conference", "assistant", "orchestrator", "multi-agent"))
                                .examples(List.of(
                                        "I'm arriving Thursday afternoon — what sessions should I attend and where should I eat?",
                                        "Plan my day at DevConf tomorrow",
                                        "I'm a Java developer interested in AI — recommend sessions and nearby lunch spots"
                                ))
                                .build()
                ))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
    }
}
