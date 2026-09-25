package dev.devconf.schedule;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class ScheduleTool {

    @ConfigProperty(name = "session.data.path", defaultValue = "../../conference-data/sessions.json")
    String sessionDataPath;

    private List<Session> sessions;

    @PostConstruct
    void init() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Path path = Path.of(sessionDataPath);
        try {
            if (Files.exists(path)) {
                sessions = mapper.readValue(path.toFile(), new TypeReference<>() {});
            } else {
                try (InputStream is = getClass().getClassLoader()
                        .getResourceAsStream("sessions.json")) {
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

    @Tool("Search conference sessions by keyword, track, speaker name, or tag.")
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
            return "No sessions found matching '" + query + "'.";
        }
        StringBuilder sb = new StringBuilder();
        for (Session s : matches) {
            sb.append("- **").append(s.title()).append("**\n");
            sb.append("  Speaker: ").append(s.speaker()).append("\n");
            sb.append("  Track: ").append(s.track())
              .append(" | ").append(s.date()).append(" at ").append(s.time())
              .append(" (").append(s.duration()).append(" min)\n");
            sb.append("  Room: ").append(s.room()).append("\n\n");
        }
        return sb.toString();
    }

    @Tool("List all available conference tracks.")
    public String listTracks() {
        return "Available tracks: " + sessions.stream()
                .map(Session::track).distinct().sorted()
                .collect(Collectors.joining(", "));
    }

    @Tool("Get the schedule for a specific date (YYYY-MM-DD).")
    public String getScheduleByDate(String date) {
        List<Session> day = sessions.stream()
                .filter(s -> s.date().equals(date))
                .sorted(Comparator.comparing(Session::time))
                .toList();
        if (day.isEmpty()) return "No sessions on " + date;
        StringBuilder sb = new StringBuilder("Schedule for " + date + ":\n\n");
        for (Session s : day) {
            sb.append("- ").append(s.time()).append(" | **")
              .append(s.title()).append("** (").append(s.speaker())
              .append(") - Room ").append(s.room()).append("\n");
        }
        return sb.toString();
    }

    @Tool("Filter sessions on a given date that start at or after the specified time (HH:MM). Use this when an attendee arrives late and wants to skip earlier sessions.")
    public String filterSessionsAfterTime(String date, String time) {
        List<Session> filtered = sessions.stream()
                .filter(s -> s.date().equals(date) && s.time().compareTo(time) >= 0)
                .sorted(Comparator.comparing(Session::time))
                .toList();
        if (filtered.isEmpty()) {
            return "No sessions on " + date + " starting at or after " + time + ".";
        }
        StringBuilder sb = new StringBuilder("Sessions on " + date + " from " + time + " onwards:\n\n");
        for (Session s : filtered) {
            sb.append("- ").append(s.time()).append(" | **")
              .append(s.title()).append("** (").append(s.speaker())
              .append(") - ").append(s.track())
              .append(", Room ").append(s.room())
              .append(" (").append(s.duration()).append(" min)\n");
            sb.append("  ").append(s.description()).append("\n\n");
        }
        return sb.toString();
    }

    @Tool("Get information about a conference speaker by name.")
    public String getSpeakerInfo(String speakerName) {
        String q = speakerName.toLowerCase();
        List<Session> speakerSessions = sessions.stream()
                .filter(s -> s.speaker().toLowerCase().contains(q))
                .toList();
        if (speakerSessions.isEmpty()) {
            return "No speaker found matching '" + speakerName + "'.";
        }
        String speaker = speakerSessions.getFirst().speaker();
        StringBuilder sb = new StringBuilder("**" + speaker + "** presents:\n\n");
        for (Session s : speakerSessions) {
            sb.append("- **").append(s.title()).append("**\n");
            sb.append("  Track: ").append(s.track())
              .append(" | ").append(s.date()).append(" at ").append(s.time())
              .append(" | Room ").append(s.room()).append("\n");
            sb.append("  ").append(s.description()).append("\n\n");
        }
        return sb.toString();
    }

    public record Session(
        String id, String title, String speaker, String track,
        String room, String date, String time, int duration,
        String level, String description, List<String> tags
    ) {}
}
