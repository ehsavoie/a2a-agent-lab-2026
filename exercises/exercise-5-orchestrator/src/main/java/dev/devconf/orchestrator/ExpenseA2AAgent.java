package dev.devconf.orchestrator;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.service.V;

public interface ExpenseA2AAgent {

    @A2AClientAgent(
            a2aServerUrl = "http://localhost:8082",
            name = "Expense & Compliance Agent",
            description = "Handles expense tracking, receipt logging, and compliance checks for conference spending",
            outputKey = "response"
    )
    String ask(@V("query") String query);
}
