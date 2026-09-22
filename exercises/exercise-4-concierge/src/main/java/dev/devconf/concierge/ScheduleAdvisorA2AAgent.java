package dev.devconf.concierge;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.service.V;

public interface ScheduleAdvisorA2AAgent {

    @A2AClientAgent(
            a2aServerUrl = "http://localhost:8080",
            name = "Schedule & Content Advisor",
            description = "Answers questions about conference sessions, schedules, speakers, and talk content",
            outputKey = "response"
    )
    String ask(@V("query") String query);
}
