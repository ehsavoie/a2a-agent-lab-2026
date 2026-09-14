package dev.devconf.concierge;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AgentDiscoveryService {

    private static final Logger LOG = Logger.getLogger(AgentDiscoveryService.class);

    @ConfigProperty(name = "concierge.agent-urls")
    List<String> agentUrls;

    private final Map<String, AgentInfo> registry = new LinkedHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public record AgentInfo(String name, String url, List<String> skills, String description) {}

    @PostConstruct
    void discoverAgents() {
        for (String baseUrl : agentUrls) {
            try {
                String cardUrl = baseUrl.endsWith("/")
                        ? baseUrl + ".well-known/agent.json"
                        : baseUrl + "/.well-known/agent.json";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(cardUrl))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode card = mapper.readTree(response.body());
                    String name = card.path("name").asText("unknown");
                    String description = card.path("description").asText("");

                    List<String> skills = new ArrayList<>();
                    if (card.has("skills")) {
                        for (JsonNode skill : card.get("skills")) {
                            skills.add(skill.path("name").asText() + ": " +
                                    skill.path("description").asText());
                        }
                    }

                    registry.put(name, new AgentInfo(name, baseUrl, skills, description));
                    LOG.infof("Discovered agent: %s at %s with %d skills",
                            name, baseUrl, skills.size());
                } else {
                    LOG.warnf("Failed to fetch AgentCard from %s: HTTP %d", cardUrl, response.statusCode());
                }
            } catch (Exception e) {
                LOG.warnf("Could not discover agent at %s: %s", baseUrl, e.getMessage());
            }
        }
        LOG.infof("Agent discovery complete: %d agents registered", registry.size());
    }

    public Collection<AgentInfo> getAvailableAgents() {
        return registry.values();
    }

    public Optional<AgentInfo> getAgentByName(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    public String buildSkillsSummary() {
        return registry.values().stream()
                .map(agent -> "Agent \"" + agent.name() + "\" — " + agent.description()
                        + "\n  Skills: " + String.join("; ", agent.skills()))
                .collect(Collectors.joining("\n\n"));
    }
}
