package dev.devconf.session;

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
public class SessionAgentCardProducer {

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
                                .id("session-search")
                                .name("Session Search")
                                .description("Search for conference sessions by topic, technology, speaker, or track.")
                                .tags(List.of("sessions", "search", "conference", "schedule"))
                                .examples(List.of(
                                        "What AI sessions are available?",
                                        "Show me talks about Kubernetes",
                                        "What is Dmytro Liubarskyi speaking about?"
                                ))
                                .build(),
                        AgentSkill.builder()
                                .id("session-recommend")
                                .name("Session Recommendations")
                                .description("Get personalized session recommendations based on your interests and experience.")
                                .tags(List.of("recommendations", "personalized", "interests"))
                                .examples(List.of(
                                        "I'm a Java developer interested in microservices, what should I attend?",
                                        "Recommend sessions for someone new to AI",
                                        "What are the must-see talks on Thursday?"
                                ))
                                .build()
                ))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
    }
}
