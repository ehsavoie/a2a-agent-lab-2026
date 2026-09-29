package dev.devconf.orchestrator;

import dev.langchain4j.agentic.a2a.A2AContextId;
import dev.langchain4j.agentic.a2a.A2ATaskId;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;

public interface ExpenseA2AAgent {

    @A2AClientAgent(
            a2aServerUrl = "http://localhost:8080/.well-known/expense/agent-card.json",
            name = "Expense & Compliance Agent",
            description = "Logs receipts, validates expenses against corporate compliance limits, and generates expense summaries. Use when the attendee mentions a taxi, meal, hotel, or any spending to log or check.",
            outputKey = "expense-response"
    )
    ResultWithAgenticScope<String> ask(@V("query") String query, @A2AContextId @V("contextId") String contextId, @A2ATaskId @V("taskId") String taskId);
}
