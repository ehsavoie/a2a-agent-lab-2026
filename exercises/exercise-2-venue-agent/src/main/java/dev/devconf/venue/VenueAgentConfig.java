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
        // TODO: Use AgentCard.builder() to build and return the AgentCard.
        // Set name, description, version from the @Value injected fields.
        // Set supportedInterfaces with a single AgentInterface using TransportProtocol.HTTP_JSON.asString() and agentUrl.
        // Set capabilities (streaming: false, pushNotifications: false).
        // Set skills (see workshop instructions).
        // Set defaultInputModes and defaultOutputModes to List.of("text").
        return null;
    }

    @Bean
    public AgentExecutor agentExecutor(VenueService venueService) {
        // TODO: Return an anonymous AgentExecutor implementation.
        //
        // In execute(RequestContext, AgentEmitter):
        //   1. Extract user text from context.getMessage() using extractText()
        //   2. Call emitter.startWork()
        //   3. Call venueService.chat(userText) to get the LLM response
        //   4. On success: emitter.addArtifact(Collections.singletonList(new TextPart(response)), null, "response", null), then emitter.complete()
        //   5. On exception: emitter.addArtifact(...) with error message, then emitter.fail()
        //
        // In cancel(RequestContext, AgentEmitter):
        //   Throw new TaskNotCancelableError()
        return null;
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
