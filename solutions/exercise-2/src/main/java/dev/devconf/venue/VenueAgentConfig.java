package dev.devconf.venue;

import java.util.Collections;
import java.util.List;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.service.AiServices;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VenueAgentConfig {

    @Value("${a2a.agent.name}")
    private String agentName;

    @Value("${a2a.agent.description}")
    private String agentDescription;

    @Value("${a2a.agent.version}")
    private String agentVersion;

    @Value("${a2a.agent.url}")
    private String agentUrl;

    @Bean
    public ChatModel chatModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.log-requests}") boolean logRequests,
            @Value("${langchain4j.open-ai.chat-model.log-responses}") boolean logResponses) {
        return OpenAiResponsesChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-6-luna")
                .reasoningEffort("medium")
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }

    @Bean
    public VenueService venueService(ChatModel model, VenueTool venueTool) {
        return AiServices.builder(VenueService.class)
                .chatModel(model)
                .tools(venueTool)
                .build();
    }

    @Bean
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name(agentName)
                .description(agentDescription)
                .version(agentVersion)
                .supportedInterfaces(List.of(
                    new AgentInterface(
                        TransportProtocol.HTTP_JSON.asString(), agentUrl)
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

    @Bean
    public AgentExecutor agentExecutor(VenueService venueService) {
        return new AgentExecutor() {

            @Override
            public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
                String userText = extractText(context.getMessage());

                emitter.startWork();

                try {
                    String response = venueService.chat(userText);
                    emitter.addArtifact(
                            Collections.singletonList(new TextPart(response)),
                            null, "response", null);
                    emitter.complete();
                } catch (Exception e) {
                    emitter.addArtifact(
                            Collections.singletonList(new TextPart("Error: " + e.getMessage())),
                            null, "error", null);
                    emitter.fail();
                }
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
                throw new TaskNotCancelableError();
            }
        };
    }

    private String extractText(Message message) {
        if (message == null || message.parts() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                sb.append(textPart.text());
            }
        }
        return sb.toString().trim();
    }
}
