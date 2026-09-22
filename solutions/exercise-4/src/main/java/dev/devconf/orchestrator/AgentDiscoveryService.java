package dev.devconf.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class AgentDiscoveryService {

    private static final Logger LOG = Logger.getLogger(AgentDiscoveryService.class);

    @ConfigProperty(name = "orchestrator.agent-urls")
    List<String> agentUrls;

    private final Map<String, AgentInfo> registry = new LinkedHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record AgentInfo(String name, String url, List<String> skills, String description) {}

    @PostConstruct
    void discoverAgents() {
        for (String baseUrl : agentUrls) {
            try {
                String cardUrl = baseUrl + "/.well-known/agent.json";
                HttpResponse<String> response = httpClient.send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(cardUrl))
                                .GET()
                                .timeout(Duration.ofSeconds(10))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode card = mapper.readTree(response.body());
                    String name = card.path("name").asText();
                    String description = card.path("description").asText();
                    List<String> skills = new ArrayList<>();
                    JsonNode skillsNode = card.path("skills");
                    if (skillsNode.isArray()) {
                        for (JsonNode skill : skillsNode) {
                            skills.add(skill.path("name").asText()
                                    + ": " + skill.path("description").asText());
                        }
                    }
                    registry.put(name, new AgentInfo(name, baseUrl, skills, description));
                    LOG.infof("Discovered agent: %s at %s with %d skills", name, baseUrl, skills.size());
                }
            } catch (Exception e) {
                LOG.warnf("Failed to discover agent at %s: %s", baseUrl, e.getMessage());
            }
        }
        LOG.infof("Agent discovery complete: %d agents registered", registry.size());
    }

    public String buildSkillsSummary() {
        return registry.values().stream()
                .map(a -> "Agent \"" + a.name() + "\" (" + a.url() + ")\n  Description: "
                        + a.description() + "\n  Skills:\n"
                        + a.skills().stream()
                                .map(s -> "    - " + s)
                                .collect(Collectors.joining("\n")))
                .collect(Collectors.joining("\n\n"));
    }

    public Optional<AgentInfo> getAgentByName(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    public Map<String, AgentInfo> getRegistry() {
        return registry;
    }
}
