package dev.devconf.session;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;

public class SessionTool {

    private final List<Session> sessions;

    public SessionTool() {
        this(System.getProperty("session.data.path", "../../conference-data/sessions.json"));
    }

    public SessionTool(String sessionsJsonPath) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Path path = Path.of(sessionsJsonPath);
            if (Files.exists(path)) {
                sessions = mapper.readValue(path.toFile(), new TypeReference<>() {});
            } else {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("sessions.json")) {
                    if (is != null) {
                        sessions = mapper.readValue(is, new TypeReference<>() {});
                    } else {
                        throw new RuntimeException(
                                "Cannot find session data at " + sessionsJsonPath + " or on classpath");
                    }
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
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
                    + sessions.stream().map(Session::track).distinct().sorted()
                            .collect(Collectors.joining(", "));
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
        return "Available tracks: " + sessions.stream()
                .map(Session::track)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
    }

    @Tool("Get the full conference schedule for a specific date (format: YYYY-MM-DD).")
    public String getScheduleByDate(String date) {
        List<Session> daySchedule = sessions.stream()
                .filter(s -> s.date().equals(date))
                .sorted((a, b) -> a.time().compareTo(b.time()))
                .toList();

        if (daySchedule.isEmpty()) {
            return "No sessions found for " + date + ". Conference dates: "
                    + sessions.stream().map(Session::date).distinct().sorted()
                            .collect(Collectors.joining(", "));
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
