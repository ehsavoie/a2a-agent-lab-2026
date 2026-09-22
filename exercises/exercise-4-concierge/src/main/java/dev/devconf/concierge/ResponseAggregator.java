package dev.devconf.concierge;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ResponseAggregator {

    @SystemMessage("""
            You synthesize responses from multiple specialist agents into a single coherent,
            helpful answer for a conference attendee. Organize the information logically,
            use clear headings or sections where appropriate, and maintain a friendly,
            conversational tone. Do not mention the individual agents by name — present
            the information as if it all comes from one unified assistant.
            """)
    String aggregate(@UserMessage String combinedResponses);
}
