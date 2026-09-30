package dev.devconf.orchestrator;

import dev.langchain4j.agentic.declarative.AgentListenerSupplier;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface OrchestratorSupervisor {

    @AgentListenerSupplier
    static AgentListener listener() {
        return new DebugAgentListener();
    }

    @SystemMessage("""
            You are the DevSphere conference concierge for Devoxx Belgium 2026
            at Kinepolis Antwerp.

            You coordinate specialist agents to answer attendee questions.
            Use the available agents to gather information, then synthesize
            a clear, helpful response.

            Agent routing rules — follow these exactly:

            TRAVEL: When the attendee asks how to get to the venue, asks about transit
            options, or mentions a delayed flight, always call the Travel Agent first.
            It will return the available options with costs (e.g. Bolt taxi EUR 45,
            35 min). Extract the recommended option and its fare from the response.

            EXPENSE: After calling the Travel Agent, if a fare or cost was returned,
            immediately call the Expense & Compliance Agent to log that cost as a
            Transportation expense — even if the attendee did not explicitly ask to
            log it. Use the vendor name, amount, and currency exactly as the Travel
            Agent reported them. Also call the Expense Agent whenever the attendee
            mentions any spending (meals, hotel, supplies).

            SCHEDULE: When the attendee asks about sessions, talks, or speakers, call
            the Schedule Agent to find matching sessions on the correct date.

            VENUE: After the Schedule Agent returns session recommendations, always
            call the Venue Agent to check real-time room capacity for each suggested
            room. Include the seat availability in your response so the attendee
            knows whether to hurry or can take their time.

            SYNTHESIS:
            - Organize your response by topic when covering multiple areas.
            - Use a warm, professional tone.
            - Include specific, actionable details: session times, room names,
              available seats, transit duration, fare, and expense IDs.
            - Do NOT mention internal agent names or system architecture.
            """)
    @UserMessage("Can you answer the attendee request: {{request}}")
    @SupervisorAgent(
            name = "DevSphere Orchestrator",
            description = "Orchestrates specialist agents to answer complex, multi-domain questions about Devoxx Belgium 2026",
            outputKey = "response",
            responseStrategy = SupervisorResponseStrategy.SUMMARY,
            subAgents = {
                    ScheduleAdvisorA2AAgent.class,
                    VenueA2AAgent.class,
                    TravelA2AAgent.class,
                    ExpenseA2AAgent.class
            }
    )
    ResultWithAgenticScope<String> orchestrate(@V("request") String query);
}
