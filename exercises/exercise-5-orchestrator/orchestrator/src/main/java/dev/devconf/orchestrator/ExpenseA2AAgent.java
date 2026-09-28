package dev.devconf.orchestrator;

import dev.langchain4j.agentic.a2a.A2AContextId;
import dev.langchain4j.agentic.a2a.A2ATaskId;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.service.V;

public interface ExpenseA2AAgent {

    @A2AClientAgent(
            a2aServerUrl = "http://localhost:8080",
            name = "Expense & Compliance Agent",
            description = "Handles expense tracking, receipt logging, and compliance checks for conference spending",
            outputKey = "expense-response"
    )
    String ask(@V("query") String query, @A2AContextId @V("contextId") String contextId, @A2ATaskId @V("taskId") String taskId);
}
