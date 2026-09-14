package dev.devconf.session;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;

@ApplicationScoped
public class SessionAgentCardProducer {

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name("Session Recommender Agent")
                .description("Recommends DevConf 2026 sessions based on attendee interests, tracks, and schedule.")
                .version("1.0.0")
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), "http://localhost:8082")
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
