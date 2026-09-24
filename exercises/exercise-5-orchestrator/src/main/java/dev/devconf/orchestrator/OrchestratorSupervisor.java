package dev.devconf.orchestrator;

import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface OrchestratorSupervisor {

    @SystemMessage("""
            You are the DevSphere conference concierge for Devoxx Belgium 2026
            at Kinepolis Antwerp.

            You coordinate specialist agents to answer attendee questions.
            Use the available agents to gather information, then synthesize
            a clear, helpful response.

            Guidelines:
            - Route each aspect of the question to the appropriate agent.
            - Organize your response by topic when covering multiple areas.
            - Use a warm, professional tone.
            - Include specific, actionable details (times, rooms, directions).
            - Do NOT mention internal agent names or system architecture.
            """)
    @SupervisorAgent(
            name = "DevSphere Orchestrator",
            description = "Orchestrates specialist agents to answer complex, multi-domain questions about Devoxx Belgium 2026",
            subAgents = {
                    ScheduleAdvisorA2AAgent.class,
                    VenueA2AAgent.class,
                    TravelA2AAgent.class,
                    ExpenseA2AAgent.class
            }
    )
    @UserMessage("{query}")
    String orchestrate(@V("query") String query);
}
