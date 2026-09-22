package dev.devconf.orchestrator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class OrchestratorAgentExecutorProducer {

    private static final Logger LOG = Logger.getLogger(OrchestratorAgentExecutorProducer.class);

    @Inject
    AgentDiscoveryService discoveryService;

    @Inject
    QueryDecomposer queryDecomposer;

    @Inject
    ResponseAggregator responseAggregator;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

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
                                "No specialist agents are currently available.")),
                                null, "response", null);
                        emitter.complete();
                        return;
                    }

                    LOG.infof("Decomposing query: %s", userText);
                    String decomposedJson = queryDecomposer.decompose(userText, skillsSummary);
                    LOG.infof("Decomposed into: %s", decomposedJson);
                    List<SubTask> subTasks = parseSubTasks(decomposedJson);

                    if (subTasks.isEmpty()) {
                        emitter.addArtifact(Collections.singletonList(new TextPart(
                                "I couldn't determine which agents to consult. Could you rephrase?")),
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

                        LOG.infof("Dispatching to %s: %s", subTask.agentName(), subTask.query());
                        String response = dispatchToAgent(agentInfo.get().url(), subTask.query());
                        agentResponses.add("=== Response from " + subTask.agentName() + " ===\n" + response);
                    }

                    String combined = "Original question: " + userText + "\n\n"
                            + String.join("\n\n", agentResponses);
                    String finalAnswer = responseAggregator.aggregate(combined);

                    emitter.addArtifact(Collections.singletonList(new TextPart(finalAnswer)),
                            null, "response", null);
                    emitter.complete();
                } catch (Exception e) {
                    LOG.errorf(e, "Orchestration failed");
                    emitter.addArtifact(
                            Collections.singletonList(new TextPart("Orchestration error: " + e.getMessage())),
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

    List<SubTask> parseSubTasks(String json) {
        try {
            String cleaned = json.strip();
            // Strip markdown code fences if the LLM wraps the JSON
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }
            int start = cleaned.indexOf('[');
            int end = cleaned.lastIndexOf(']');
            if (start >= 0 && end > start) {
                cleaned = cleaned.substring(start, end + 1);
            }
            return mapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            LOG.warnf("Failed to parse sub-tasks JSON: %s — raw: %s", e.getMessage(), json);
            return List.of();
        }
    }

    String dispatchToAgent(String agentUrl, String query) throws Exception {
        Map<String, Object> payload = Map.of(
                "jsonrpc", "2.0",
                "method", "message/send",
                "params", Map.of(
                        "message", Map.of(
                                "role", "user",
                                "parts", List.of(Map.of("type", "text", "text", query))
                        )
                ),
                "id", "orch-" + System.currentTimeMillis()
        );

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(agentUrl + "/a2a"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                        .timeout(Duration.ofSeconds(120))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        JsonNode root = mapper.readTree(response.body());

        // Try standard /a2a path first; fall back to root path for Python agents
        if (root.has("error") && response.statusCode() != 200) {
            response = httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(agentUrl))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                            .timeout(Duration.ofSeconds(120))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            root = mapper.readTree(response.body());
        }

        JsonNode result = root.path("result");
        JsonNode artifacts = result.path("artifacts");
        if (artifacts.isArray() && !artifacts.isEmpty()) {
            JsonNode parts = artifacts.get(0).path("parts");
            if (parts.isArray() && !parts.isEmpty()) {
                return parts.get(0).path("text").asText("[No text in response]");
            }
        }
        return "[No artifacts in response]";
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
