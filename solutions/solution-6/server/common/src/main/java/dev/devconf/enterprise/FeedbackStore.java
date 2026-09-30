package dev.devconf.enterprise;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class FeedbackStore {

    @PersistenceContext(unitName = "a2a-java")
    EntityManager em;

    @Transactional
    public void add(Feedback feedback) {
        em.persist(feedback);
    }

    public List<Feedback> forSpeaker(String speaker) {
        return em.createQuery(
                        "SELECT f FROM Feedback f WHERE LOWER(f.speaker) LIKE :pattern ORDER BY f.submittedAt",
                        Feedback.class)
                .setParameter("pattern", "%" + normalise(speaker) + "%")
                .getResultList();
    }

    public List<Feedback> forSession(String speaker, String sessionFragment) {
        return em.createQuery(
                        "SELECT f FROM Feedback f WHERE LOWER(f.speaker) LIKE :speaker"
                                + " AND LOWER(f.sessionTitle) LIKE :session ORDER BY f.submittedAt",
                        Feedback.class)
                .setParameter("speaker", "%" + normalise(speaker) + "%")
                .setParameter("session", "%" + normalise(sessionFragment) + "%")
                .getResultList();
    }

    public String buildSummary(String speaker, String sessionFragment) {
        List<Feedback> entries = sessionFragment == null || sessionFragment.isBlank()
                ? forSpeaker(speaker)
                : forSession(speaker, sessionFragment);

        if (entries.isEmpty()) {
            return "No feedback found for " + speaker
                    + (sessionFragment != null && !sessionFragment.isBlank() ? " / " + sessionFragment : "") + ".";
        }

        double avg = entries.stream().mapToInt(Feedback::getRating).average().orElse(0);
        StringBuilder sb = new StringBuilder();
        sb.append("Feedback summary for ").append(speaker).append("\n");
        sb.append("─".repeat(40)).append("\n");
        sb.append(String.format("Responses: %d  |  Average rating: %.1f/5%n%n", entries.size(), avg));

        Map<String, List<Feedback>> bySession = new TreeMap<>();
        for (Feedback f : entries) {
            String key = f.getSessionTitle() != null ? f.getSessionTitle() : "(unspecified session)";
            bySession.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }

        bySession.forEach((session, list) -> {
            double sessionAvg = list.stream().mapToInt(Feedback::getRating).average().orElse(0);
            sb.append(String.format("Session: %s  (%.1f/5)%n", session, sessionAvg));
            list.forEach(f -> {
                String stars = "★".repeat(f.getRating()) + "☆".repeat(5 - f.getRating());
                sb.append(String.format("  %s  \"%s\"%n", stars, f.getComment()));
            });
            sb.append("\n");
        });

        return sb.toString().trim();
    }

    private static String normalise(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}
