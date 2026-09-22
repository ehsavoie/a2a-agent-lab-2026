package dev.devconf.venue;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface VenueService {

    @SystemMessage("""
            You are the DevConf 2026 Venue & On-Site Operations Agent.
            You manage real-time venue information including room capacity via IoT sensors,
            indoor navigation, and catering queue tracking.
            You can reserve fast-track entry passes for attendees.
            Always provide specific room names and capacity percentages.
            """)
    String chat(@UserMessage String userMessage);
}
