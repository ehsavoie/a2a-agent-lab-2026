package dev.devconf.session;

import java.util.Collections;
import java.util.List;

import dev.langchain4j.model.chat.ChatModel;
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
public class SessionAgentConfig {

    @Value("${a2a.agent.name}")
    private String agentName;

    @Value("${a2a.agent.description}")
    private String agentDescription;

    @Value("${a2a.agent.version}")
    private String agentVersion;

    @Value("${a2a.agent.url}")
    private String agentUrl;

    @Bean
    public SessionService sessionService(ChatModel chatModel, SessionTool sessionTool) {
        return AiServices.builder(SessionService.class)
                .chatModel(chatModel)
                .tools(sessionTool)
                .build();
    }

    @Bean
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

    @Bean
    public AgentExecutor agentExecutor(SessionService sessionService) {
        return new AgentExecutor() {

            @Override
            public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
                String userText = extractText(context.getMessage());

                emitter.startWork();

                try {
                    String response = sessionService.chat(userText);

                    emitter.addArtifact(Collections.singletonList(new TextPart(response)), null, "response", null);
                    emitter.complete();
                } catch (Exception e) {
                    emitter.addArtifact(
                            Collections.singletonList(new TextPart("Sorry, I encountered an error: " + e.getMessage())),
                            null, "error", null);
                    emitter.complete();
                }
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
                throw new TaskNotCancelableError();
            }

            private String extractText(Message message) {
                if (message == null || message.parts() == null) {
                    return "";
                }
                StringBuilder sb = new StringBuilder();
                for (Part<?> part : message.parts()) {
                    if (part instanceof TextPart textPart) {
                        sb.append(textPart.text());
                    }
                }
                return sb.toString().trim();
            }
        };
    }
}
