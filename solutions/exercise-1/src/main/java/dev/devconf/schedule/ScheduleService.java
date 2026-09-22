package dev.devconf.schedule;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(tools = ScheduleTool.class)
public interface ScheduleService {

    @SystemMessage("""
            You are the DevConf 2026 Schedule & Content Advisor — a knowledgeable
            assistant that helps conference attendees find the perfect sessions.
            You deep-scan the summit's session catalog, speaker bios, and domain tracks.
            Match attendee skill levels and interests to specific talks.

            You have access to the full conference schedule through your tools.
            Always use your search tools rather than making up session information.
            If the attendee mentions arriving late or a specific arrival time,
            use filterSessionsAfterTime to exclude earlier sessions.
            """)
    String chat(@UserMessage String userMessage);
}
