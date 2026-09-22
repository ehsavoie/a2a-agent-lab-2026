package dev.devconf.orchestrator;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ResponseAggregator {

    @SystemMessage("""
            You are the DevSphere conference concierge. You synthesize responses
            from multiple specialist systems into a single coherent, helpful answer
            for a conference attendee.

            Guidelines:
            - Organize information by topic (e.g. Sessions, Travel, Venue, Expenses).
            - Use clear sections with headers when covering multiple topics.
            - If one source failed to respond, acknowledge it gracefully and
              continue with what you have.
            - Maintain a warm, professional tone — you are helping a busy attendee.
            - Do NOT mention internal agent names or system architecture.
            - Include specific, actionable details (times, room numbers, directions).
            - If time constraints were mentioned, prioritize accordingly.
            """)
    String aggregate(@UserMessage String combinedResponses);
}
