package dev.devconf.enterprise;

import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;

@ApplicationScoped
public class EnterpriseAgentExecutorProducer {

    @Inject
    FeedbackStore feedbackStore;

    @Produces
    @ApplicationScoped
    public AgentExecutor enterpriseAgentExecutor() {
        return new FeedbackAgentExecutor(feedbackStore);
    }

    private static class FeedbackAgentExecutor implements AgentExecutor {

        // "rating: 4" | "4/5" | "4 stars" | "rated it 4"
        private static final Pattern RATING_PATTERN =
                Pattern.compile("(?:rating[:\\s]+|rated(?:\\s+it)?\\s+|\\b)(\\d)[/\\s]*(?:5|stars?)?",
                        Pattern.CASE_INSENSITIVE);

        // "for [Speaker Name]" or "about [Speaker Name]"
        private static final Pattern FOR_SPEAKER_PATTERN =
                Pattern.compile("(?:for|about)\\s+([A-Za-z]+(?: [A-Za-z]+){0,3})",
                        Pattern.CASE_INSENSITIVE);

        // "session: [title]" — stops at comma
        private static final Pattern SESSION_PATTERN =
                Pattern.compile("(?:session|talk|presentation)[:\\s]+\"?([^\"\\n,]+)\"?",
                        Pattern.CASE_INSENSITIVE);

        private final FeedbackStore store;

        FeedbackAgentExecutor(FeedbackStore store) {
            this.store = store;
        }

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            boolean isNewTask = context.getTask() == null;
            if (isNewTask) {
                emitter.submit();
            }

            // Demo-only convention (not a general A2A pattern): a message with messageId "init"
            // only creates the task and returns, leaving it open for a later continuation
            // message to do the work. This lets a client observe the task via a different node
            // than the one that eventually completes it, proving cross-node replication.
            boolean isInitHandshake = isNewTask && "init".equals(context.getMessage().messageId());
            if (isInitHandshake) {
                return;
            }

            emitter.startWork();
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            String text = extractText(context.getMessage().parts());
            String response = isSummaryRequest(text)
                    ? handleSummaryRequest(text)
                    : handleFeedbackSubmission(text);

            emitter.addArtifact(
                    Collections.singletonList(new TextPart(response)),
                    null, "response", null);
            emitter.complete();
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
            throw new TaskNotCancelableError();
        }

        // ── Intent detection ────────────────────────────────────────────────────

        private boolean isSummaryRequest(String text) {
            String lower = text.toLowerCase();
            return lower.contains("summary") || lower.contains("my feedback")
                    || lower.contains("what feedback") || lower.contains("feedback received")
                    || lower.contains("how did") || lower.contains("reviews");
        }

        // ── Feedback submission ──────────────────────────────────────────────────

        private String handleFeedbackSubmission(String text) {
            String speaker = extractSpeaker(text);
            String session = extractSession(text);
            int    rating  = extractRating(text);
            String comment = extractComment(text);

            if (speaker.isBlank()) {
                return "Please specify the speaker name, e.g. \"Feedback for Mario Fusco, rating: 5, great talk!\"";
            }
            if (rating < 1) {
                return "Please include a rating (1–5), e.g. \"rating: 5\" or \"5 stars\".";
            }

            store.add(new Feedback(speaker, session, rating, comment));

            String stars = "★".repeat(rating) + "☆".repeat(5 - rating);
            return String.format("Thank you! Feedback recorded for %s%s%n%s  \"%s\"",
                    speaker,
                    session != null ? " — " + session : "",
                    stars,
                    comment);
        }

        // ── Summary request ──────────────────────────────────────────────────────

        private String handleSummaryRequest(String text) {
            String speaker = extractSpeaker(text);
            String session = extractSession(text);

            if (speaker.isBlank()) {
                return "Please specify the speaker name, e.g. \"Summary for Mario Fusco\".";
            }
            return store.buildSummary(speaker, session);
        }

        // ── Extraction helpers ───────────────────────────────────────────────────

        private String extractText(List<Part<?>> parts) {
            return parts.stream()
                    .filter(p -> p instanceof TextPart)
                    .map(p -> ((TextPart) p).text())
                    .reduce("", (a, b) -> a + " " + b)
                    .trim();
        }

        private String extractSpeaker(String text) {
            Matcher m = FOR_SPEAKER_PATTERN.matcher(text);
            return m.find() ? m.group(1).trim() : "";
        }

        private String extractSession(String text) {
            Matcher m = SESSION_PATTERN.matcher(text);
            return m.find() ? m.group(1).trim() : null;
        }

        private int extractRating(String text) {
            Matcher m = RATING_PATTERN.matcher(text);
            if (m.find()) {
                try { return Math.min(5, Math.max(1, Integer.parseInt(m.group(1)))); }
                catch (NumberFormatException ignored) {}
            }
            return -1;
        }

        private String extractComment(String text) {
            String comment = text;
            comment = FOR_SPEAKER_PATTERN.matcher(comment).replaceFirst("").trim();
            comment = SESSION_PATTERN.matcher(comment).replaceFirst("").trim();
            comment = RATING_PATTERN.matcher(comment).replaceFirst("").trim();
            // Collapse consecutive commas left by removing structured fields (e.g. "Feedback , , , comment")
            comment = comment.replaceAll("(,\\s*){2,}", ", ").trim();
            // Strip a leading label word like "Feedback" that preceded the structured fields
            comment = comment.replaceAll("^\\w+\\s*,\\s*", "").trim();
            comment = comment.replaceAll("^[,:\\-–—]+\\s*", "").trim();
            return comment.isBlank() ? "(no comment)" : comment;
        }
    }
}
