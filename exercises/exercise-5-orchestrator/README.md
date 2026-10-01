# Exercise 5 — The Orchestrator & Concierge (25 min)

> *"Maya types: 'My flight was delayed so I missed the morning shuttle. I'm interested in agentic AI and Java agents — what talks should I catch today, how do I get to the venue quickly, and can you log my taxi receipt?' No single agent can answer this. You need an orchestrator."*

Build the **DevSphere Orchestrator** — a Quarkus REST application with `@SupervisorAgent` and `@A2AClientAgent` annotations that lets an LLM autonomously route Maya's complex multi-domain query across all four specialist agents and synthesize a unified response.

## Architecture

```
Browser / curl
     |
     v  POST /api/query
+------------------------------------+
|  Orchestrator (Quarkus) :8090      |
|  REST app + Web UI                 |
|                                    |
|  @SupervisorAgent                  |
|   LLM decides which agents to call |
|   then synthesizes the final answer|
|                                    |
|  @A2AClientAgent sub-agents        |
|  (all point to Concierge :8080)    |
|  /.well-known/schedule/agent-card  |
|  /.well-known/venue/agent-card     |
|  /.well-known/travel/agent-card    |
|  /.well-known/expense/agent-card   |
+------------------------------------+
     |
     v
+------------------------------------+
|  Concierge (Quarkus) :8080         |
|  Multi-tenant: Schedule + Venue +  |
|  Travel + Expense agents, each     |
|  at its own AgentCard path         |
+------------------------------------+
```

## What You Will Study

The code is **fully implemented** — this exercise is about understanding LangChain4j's agentic orchestration patterns and running Maya's complete scenario.

This exercise has two sub-projects:
- **`concierge/`** — multi-tenant Quarkus app hosting all four specialist agents
- **`orchestrator/`** — Quarkus REST app with Supervisor + Web UI

## Key Patterns

### `@A2AClientAgent` — Sub-agent declaration

Each downstream A2A agent is wrapped as a LangChain4j sub-agent. The framework automatically fetches the AgentCard and handles A2A communication:

```java
public interface ScheduleAdvisorA2AAgent {

    @A2AClientAgent(
            a2aServerUrl = "http://localhost:8080/.well-known/schedule/agent-card.json",
            name = "Schedule & Content Advisor",
            description = "Answers questions about conference sessions, schedules, speakers, and talk content",
            outputKey = "schedule-response"
    )
    ResultWithAgenticScope<String> ask(
        @V("query") String query,
        @A2AContextId @V("contextId") String contextId,
        @A2ATaskId @V("taskId") String taskId
    );
}
// Similarly: VenueA2AAgent, TravelA2AAgent, ExpenseA2AAgent
// All pointing to the Concierge on :8080
```

### `@SupervisorAgent` — LLM-driven orchestration

The `@SupervisorAgent` is the brain of the orchestrator. The LLM autonomously decides which sub-agents to call based on the user's question, then synthesizes a unified response. No manual routing needed.

```java
public interface OrchestratorSupervisor {

    @SystemMessage("""
            You are the DevSphere conference concierge for Devoxx Belgium 2026
            at Kinepolis Antwerp.
            You coordinate specialist agents to answer attendee questions.
            """)
    @UserMessage("Can you answer the attendee request: {{request}}")
    @SupervisorAgent(
            name = "DevSphere Orchestrator",
            subAgents = {
                    ScheduleAdvisorA2AAgent.class,
                    VenueA2AAgent.class,
                    TravelA2AAgent.class,
                    ExpenseA2AAgent.class
            }
    )
    ResultWithAgenticScope<String> orchestrate(@V("request") String query);
}
```

### REST Endpoint

The orchestrator is **not** an A2A agent itself — it's the user-facing entry point. It exposes `POST /api/query` and a built-in web UI.

## Quick Start

Requires the Concierge and Orchestrator running simultaneously.

```bash
# Start the infrastructure (Grafana LGTM observability)
cd exercises/exercise-5-orchestrator
podman-compose up -d   # or: docker compose up -d

# Terminal 1 — Start the Concierge (all specialist agents on :8080)
cd exercises/exercise-5-orchestrator/concierge
export OPENAI_API_KEY=your-api-key-here
mvn quarkus:dev

# Terminal 2 — Start the Orchestrator
cd exercises/exercise-5-orchestrator/orchestrator
export OPENAI_API_KEY=your-api-key-here
mvn quarkus:dev
```

## Verify

```bash
# Send Maya's full scenario — spans all four agents
curl -s -X POST http://localhost:8090/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "My flight was delayed so I missed the morning shuttle. What is the fastest way to get from Brussels Airport to Kinepolis Antwerp right now, and how much will it cost? I am interested in agentic AI and Java agents — which talks are still on today and do the rooms still have space? Once you have the transport cost, please log it as an expense for me and check my compliance status."
  }' | jq .response

# Check compliance separately
curl -s -X POST http://localhost:8090/api/query \
  -H "Content-Type: application/json" \
  -d '{"query": "Can you show me the full expense compliance report for Maya?"}' | jq .response
```

> **Or use the Web UI:** open [http://localhost:8090](http://localhost:8090) for an interactive chat interface.

### What the orchestrator does with Maya's query

The `@SupervisorAgent` LLM fans the request out across all agents:

| Agent | Action |
|---|---|
| **Travel Agent** | Compares Bolt rideshare (35 min, €45, recommended) vs. disrupted NMBS train (55 min). The taxi fare is discovered here, not supplied by Maya. |
| **Schedule Agent** | Searches for agentic AI and Java talks available on 2026-10-07. |
| **Venue Agent** | Checks real-time room capacity for each suggested session. |
| **Expense Agent** | Receives the €45 taxi cost from the Travel Agent's answer and logs it (COMPLIANT — under the €200 transport cap). |

## Observability

Grafana LGTM is running at [http://localhost:3000](http://localhost:3000). All agent traces are sent via OpenTelemetry to Grafana/Tempo — you can watch the full multi-agent trace for Maya's query in real time.

## Checkpoint

- [ ] Concierge running on port 8080 (Schedule, Venue, Travel, and Expense agents at separate `/.well-known/*` paths)
- [ ] Orchestrator running on port 8090 with web UI at the root
- [ ] `@A2AClientAgent` interfaces point to each agent's specific AgentCard URL on the concierge
- [ ] `@SupervisorAgent` lets the LLM autonomously select and call agents
- [ ] Single unified response synthesized from multiple agent answers
- [ ] `POST /api/query` returns a JSON response

## Project Structure

```
exercise-5-orchestrator/
├── concierge/           # Multi-tenant Quarkus: all 4 specialist agents
├── orchestrator/        # Supervisor + @A2AClientAgent + REST UI (:8090)
└── podman-compose.yml   # Grafana LGTM (OTel observability)
```
