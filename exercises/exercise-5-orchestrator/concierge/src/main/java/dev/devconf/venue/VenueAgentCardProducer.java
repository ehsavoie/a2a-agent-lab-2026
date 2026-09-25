package dev.devconf.venue;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import org.a2aproject.sdk.server.multitenancy.Tenant;
import org.a2aproject.sdk.server.PublicAgentCard;

@ApplicationScoped
public class VenueAgentCardProducer {

    @ConfigProperty(name = "venue.a2a.agent.name")
    String agentName;

    @ConfigProperty(name = "venue.a2a.agent.description")
    String agentDescription;

    @ConfigProperty(name = "a2a.agent.version")
    String agentVersion;

    @ConfigProperty(name = "a2a.agent.url")
    String agentUrl;

    @Produces
    @PublicAgentCard
    @Singleton
    @Tenant("venue")
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name(agentName)
                .description(agentDescription)
                .version(agentVersion)
                .supportedInterfaces(List.of(
                    new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), agentUrl, "venue")
                ))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .skills(List.of(
                    AgentSkill.builder()
                        .id("room-capacity")
                        .name("Room Capacity Check")
                        .description("Check real-time room capacity and occupancy from IoT sensors.")
                        .tags(List.of("venue", "iot", "capacity"))
                        .examples(List.of("Is Hall B full?", "How many seats are left in Room 201?"))
                        .build(),
                    AgentSkill.builder()
                        .id("indoor-map")
                        .name("Indoor Navigation")
                        .description("Get walking directions between rooms and areas in the venue.")
                        .tags(List.of("venue", "navigation", "directions"))
                        .examples(List.of("How do I get from the entrance to Hall B?"))
                        .build(),
                    AgentSkill.builder()
                        .id("catering-queue")
                        .name("Catering Queue Status")
                        .description("Check current queue lengths and wait times at catering stations.")
                        .tags(List.of("venue", "catering", "food"))
                        .examples(List.of("Where is the shortest food queue?"))
                        .build(),
                    AgentSkill.builder()
                        .id("entry-pass")
                        .name("Fast-Track Entry Pass")
                        .description("Reserve a fast-track entry pass for priority seating at a session room.")
                        .tags(List.of("venue", "pass", "reservation"))
                        .examples(List.of("Reserve a fast-track pass for Hall B"))
                        .build()
                ))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
    }
}
