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

import java.util.List;

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
                        .id("session-search")
                        .name("Session Search")
                        .description("Search for conference sessions by topic, technology, speaker, or track.")
                        .tags(List.of("sessions", "search", "schedule"))
                        .examples(List.of(
                            "What sessions about agentic AI are on October 7?",
                            "Find talks about Java agents"))
                        .build(),
                    AgentSkill.builder()
                        .id("session-recommend")
                        .name("Session Recommendations")
                        .description("Get personalized session recommendations based on interests and availability.")
                        .tags(List.of("recommendations", "personalization"))
                        .examples(List.of(
                            "I arrive at 9:45 AM on October 7 and like agentic AI. What should I attend?",
                            "Recommend sessions for a Java developer interested in microservices"))
                        .build(),
                    AgentSkill.builder()
                        .id("speaker-info")
                        .name("Speaker Information")
                        .description("Get information about conference speakers and their sessions.")
                        .tags(List.of("speakers", "bios"))
                        .examples(List.of(
                            "Tell me about the speakers covering AI topics",
                            "Who is speaking about Quarkus?"))
                        .build()
                ))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
    }
}
