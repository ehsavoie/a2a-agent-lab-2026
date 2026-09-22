package dev.devconf.concierge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ConciergeAgentExecutorProducer {

    private static final Logger LOG = Logger.getLogger(ConciergeAgentExecutorProducer.class);

    @Inject
    AgentDiscoveryService discoveryService;

    @Inject
    QueryDecomposer queryDecomposer;

    @Inject
    ResponseAggregator responseAggregator;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Produces
    public AgentExecutor agentExecutor() {
        return new AgentExecutor() {

            @Override
            public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
                String userText = extractText(context.getMessage());

                emitter.startWork();

                try {
                    String skillsSummary = discoveryService.buildSkillsSummary();
                    if (skillsSummary.isBlank()) {
                        emitter.addArtifact(Collections.singletonList(new TextPart(
                                "No specialist agents are currently available. Please ensure the Session Agent and Travel Tips Agent are running.")),
                                null, "response", null);
                        emitter.complete();
                        return;
                    }

                    String decomposedJson = queryDecomposer.decompose(userText, skillsSummary);
                    List<SubTask> subTasks = parseSubTasks(decomposedJson);

                    if (subTasks.isEmpty()) {
                        emitter.addArtifact(Collections.singletonList(new TextPart(
                                "I couldn't determine which agents to consult for your question. Could you rephrase it?")),
                                null, "response", null);
                        emitter.complete();
                        return;
                    }

                    List<String> agentResponses = new ArrayList<>();
                    for (SubTask subTask : subTasks) {
                        var agentInfo = discoveryService.getAgentByName(subTask.agentName());
                        if (agentInfo.isEmpty()) {
                            LOG.warnf("Agent '%s' not found in registry, skipping", subTask.agentName());
                            continue;
                        }

                        String response = dispatchToAgent(agentInfo.get().url(), subTask.query());
                        agentResponses.add("=== Response from " + subTask.agentName() + " ===\n" + response);
                    }

                    String combinedResponses = "Original question: " + userText + "\n\n"
                            + String.join("\n\n", agentResponses);
                    String finalAnswer = responseAggregator.aggregate(combinedResponses);

                    emitter.addArtifact(Collections.singletonList(new TextPart(finalAnswer)), null, "response", null);
                    emitter.complete();
                } catch (Exception e) {
                    LOG.errorf(e, "Concierge orchestration failed");
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
        };
    }

    private List<SubTask> parseSubTasks(String json) {
        try {
            String trimmed = json.strip();
            int start = trimmed.indexOf('[');
            int end = trimmed.lastIndexOf(']');
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start, end + 1);
            }
            return mapper.readValue(trimmed, new TypeReference<>() {});
        } catch (Exception e) {
            LOG.warnf("Failed to parse sub-tasks JSON: %s", e.getMessage());
            return List.of();
        }
    }

    private String dispatchToAgent(String agentUrl, String query) {
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "jsonrpc", "2.0",
                    "method", "message/send",
                    "id", "concierge-" + System.currentTimeMillis(),
                    "params", Map.of(
                            "message", Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("type", "text", "text", query))
                            )
                    )
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(agentUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode body = mapper.readTree(response.body());
                JsonNode artifacts = body.path("result").path("artifacts");
                if (artifacts.isArray() && !artifacts.isEmpty()) {
                    JsonNode parts = artifacts.get(0).path("parts");
                    if (parts.isArray() && !parts.isEmpty()) {
                        return parts.get(0).path("text").asText("(no text in response)");
                    }
                }
                return body.path("result").toString();
            }
            return "(Agent returned HTTP " + response.statusCode() + ")";
        } catch (Exception e) {
            LOG.warnf("Failed to dispatch to agent at %s: %s", agentUrl, e.getMessage());
            return "(Agent unavailable: " + e.getMessage() + ")";
        }
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

}
