package dev.devconf.concierge;

import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.service.SystemMessage;

public interface ConciergeSupervisor {

    @SystemMessage("""
            You are the DevConf conference concierge for Devoxx Belgium 2026
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
            name = "DevConf Concierge",
            description = "Orchestrates specialist agents to answer complex, multi-domain questions about Devoxx Belgium 2026",
            subAgents = {
                    ScheduleAdvisorA2AAgent.class,
                    TravelA2AAgent.class
            }
    )
    String concierge(String query);
}
