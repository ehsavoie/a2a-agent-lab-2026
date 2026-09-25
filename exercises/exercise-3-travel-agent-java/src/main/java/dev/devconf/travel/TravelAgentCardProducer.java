package dev.devconf.travel;

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
public class TravelAgentCardProducer {

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
                        .id("flight-status")
                        .name("Flight Status")
                        .description("Check flight status, delays, and gate information for flights arriving at Brussels Airport.")
                        .tags(List.of("flights", "delays", "gates", "arrivals"))
                        .examples(List.of(
                            "What's the status of flight UA 998?",
                            "Is my flight to Brussels delayed?"))
                        .build(),
                    AgentSkill.builder()
                        .id("transit-routes")
                        .name("Transit Routes")
                        .description("Get transit options from airport to venue with real-time disruption info.")
                        .tags(List.of("transit", "airport", "rideshare", "train", "taxi"))
                        .examples(List.of(
                            "How do I get from Brussels Airport to Kinepolis Antwerp?",
                            "What's the fastest way to the venue?"))
                        .build(),
                    AgentSkill.builder()
                        .id("hotel-search")
                        .name("Hotel Search")
                        .description("Search nearby hotels with availability and pricing.")
                        .tags(List.of("hotels", "accommodation", "booking"))
                        .examples(List.of(
                            "What hotels are near Kinepolis Antwerp?",
                            "Find me a cheap hotel close to the venue."))
                        .build(),
                    AgentSkill.builder()
                        .id("receipt-extraction")
                        .name("Receipt Extraction")
                        .description("Extract fare details from receipt descriptions for expense reporting.")
                        .tags(List.of("receipts", "expenses", "reimbursement"))
                        .examples(List.of(
                            "Log my taxi receipt for €65",
                            "Extract my Bolt rideshare fare details"))
                        .build(),
                    AgentSkill.builder()
                        .id("restaurant-search")
                        .name("Restaurant Search")
                        .description("Find restaurants near the convention center by cuisine, price, or dietary preference.")
                        .tags(List.of("food", "restaurants", "dining"))
                        .examples(List.of(
                            "Where should I eat in Antwerp?",
                            "Any good restaurants near the venue?"))
                        .build(),
                    AgentSkill.builder()
                        .id("local-tips")
                        .name("Local Tips")
                        .description("Helpful tips for conference attendees about the venue and surroundings.")
                        .tags(List.of("tips", "venue", "wifi", "coffee"))
                        .examples(List.of(
                            "Any tips for Devoxx Belgium first-timers?",
                            "What Belgian beers should I try?"))
                        .build()
                ))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
    }
}
