package dev.devconf.orchestrator;

import dev.langchain4j.agentic.a2a.A2AContextId;
import dev.langchain4j.agentic.a2a.A2ATaskId;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;

public interface ScheduleAdvisorA2AAgent {

    @A2AClientAgent(
            a2aServerUrl = "http://localhost:8080/.well-known/schedule/agent-card.json",
            name = "Schedule & Content Advisor",
            description = "Answers questions about conference sessions, schedules, speakers, and talk content",
            outputKey = "response"
    )
    ResultWithAgenticScope<String> ask(@V("query") String query, @A2AContextId @V("contextId") String contextId, @A2ATaskId @V("taskId") String taskId);
}
