package dev.devconf.session;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class SessionTool {

    @ConfigProperty(name = "session.data.path", defaultValue = "../../conference-data/sessions.json")
    String sessionDataPath;

    private List<Session> sessions;

    @PostConstruct
    void loadSessions() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Path path = Path.of(sessionDataPath);
            if (Files.exists(path)) {
                sessions = mapper.readValue(path.toFile(), new TypeReference<>() {});
            } else {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("sessions.json")) {
                    if (is != null) {
                        sessions = mapper.readValue(is, new TypeReference<>() {});
                    } else {
                        sessions = List.of();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load session data", e);
        }
    }

    @Tool("Search conference sessions by keyword, track, speaker name, or tag. Returns matching sessions with details.")
    public String searchSessions(String query) {
        String q = query.toLowerCase();
        List<Session> matches = sessions.stream()
                .filter(s -> s.title().toLowerCase().contains(q)
                        || s.track().toLowerCase().contains(q)
                        || s.speaker().toLowerCase().contains(q)
                        || s.description().toLowerCase().contains(q)
                        || s.tags().stream().anyMatch(t -> t.toLowerCase().contains(q)))
                .toList();

        if (matches.isEmpty()) {
            return "No sessions found matching '" + query + "'. Available tracks: "
                    + sessions.stream().map(Session::track).distinct().sorted().toList();
        }

        StringBuilder sb = new StringBuilder();
        for (Session s : matches) {
            sb.append("- **").append(s.title()).append("**\n");
            sb.append("  Speaker: ").append(s.speaker()).append("\n");
            sb.append("  Track: ").append(s.track())
              .append(" | Room: ").append(s.room())
              .append(" | ").append(s.date()).append(" at ").append(s.time())
              .append(" (").append(s.duration()).append(" min)\n");
            sb.append("  ").append(s.description()).append("\n\n");
        }
        return sb.toString();
    }

    @Tool("List all available conference tracks.")
    public String listTracks() {
        List<String> tracks = sessions.stream()
                .map(Session::track)
                .distinct()
                .sorted()
                .toList();
        return "Available tracks: " + String.join(", ", tracks);
    }

    @Tool("Get the full conference schedule for a specific date (format: YYYY-MM-DD).")
    public String getScheduleByDate(String date) {
        List<Session> daySchedule = sessions.stream()
                .filter(s -> s.date().equals(date))
                .sorted((a, b) -> a.time().compareTo(b.time()))
                .toList();

        if (daySchedule.isEmpty()) {
            return "No sessions found for " + date + ". Conference dates: "
                    + sessions.stream().map(Session::date).distinct().sorted().toList();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Schedule for ").append(date).append(":\n\n");
        for (Session s : daySchedule) {
            sb.append("- ").append(s.time()).append(" | **").append(s.title()).append("**")
              .append(" (").append(s.speaker()).append(") — Room ").append(s.room()).append("\n");
        }
        return sb.toString();
    }

    public record Session(
            String id,
            String title,
            String speaker,
            String track,
            String room,
            String date,
            String time,
            int duration,
            String description,
            List<String> tags
    ) {}
}
