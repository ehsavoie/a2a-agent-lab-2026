# Building the DevSphere Concierge — A Multi-Agent A2A Ecosystem

A 2-hour hands-on lab that takes you from zero to a fully orchestrated, multi-runtime, multi-language, observable agent mesh using the **A2A protocol**, **LangChain4j**, and the **A2A Java SDK**.

## The Story

DevConf 2026 is next week and 5,000 attendees need help navigating the conference. You're building **DevSphere** — an AI-powered concierge mesh of specialized agents that collaborate via A2A to answer any attendee question, from session recommendations to travel logistics to expense reporting.

### The Scenario: "The Flight Delay & Keynote Clash"

It is 8:15 AM on Day 1. Maya lands at the airport, but her flight was delayed by two hours. She opens her DevSphere app and types:

> *"My flight was delayed so I missed the morning shuttle. I'm interested in agentic AI and Java agents—what talks should I catch today, how do I get to the venue quickly, and can you log my taxi receipt?"*

Here's how the mesh resolves her request in real-time:

1. **Edge Routing & Decomposition** — The Quarkus Orchestrator receives Maya's prompt and uses its configured A2A clients to select specialist agents
2. **Travel & Transit** — The Concierge Travel tenant compares transit options and returns travel information
3. **Session Matching** — The Concierge Schedule tenant filters sessions for Maya's interests and arrival time
4. **Venue Check** — The Concierge Venue tenant checks venue information when Maya's request needs it
5. **Expense Log** — The Concierge Expense tenant asks for the receipt details needed to prepare an audit-ready reimbursement entry

Each exercise adds a new agent to the mesh. By the end, you'll have the complete DevSphere system.

## Architecture

```
                              User (Maya)
                                  |
                       ┌──────────▼──────────┐
                       │ Orchestrator        │
                       │ REST :8090          │
                       └──────────┬───────────┘
                                  │ A2A JSON-RPC
                       ┌──────────▼──────────┐
                       │ Concierge           │
                       │ Quarkus :8080       │
                       ├─────────────────────┤
                       │ Schedule tenant     │
                       │ Travel tenant       │
                       │ Venue tenant        │
                       │ Expense tenant      │
                       └─────────────────────┘

Specialist agents: AgentCard + A2A transport bindings + A2A SDK
All traces: OpenTelemetry → Grafana/Tempo (:3000)
```

| Port | Agent | Runtime | A2A SDK |
|------|-------|---------|---------|
| 8080 | Schedule Advisor (Exercise 1) or Concierge tenants (Exercise 5) | Quarkus | `a2a-java-sdk-reference-jsonrpc` |
| 8081 | Venue & On-Site Operations | Spring Boot | `a2a-spring-boot-starter-server-rest` |
| 9000 | Travel & Logistics | Python | `a2a-sdk` (Python) |
| 8090 | Orchestrator REST API | Quarkus | `a2a-java-sdk-client` |
| 8082 | Expense & Compliance | WildFly 41 (Jakarta EE) | `a2a-jakarta-jsonrpc` + `a2a-jakarta-rest` |

For Exercise 5, run the Concierge on port 8080 with all four tenants and the Orchestrator on port 8090. The standalone agent services from Exercises 1–4 are not part of that setup.

## How It All Works

### Sequence 1: Single Agent Request

When a client sends a message to any agent, this is the flow through the A2A SDK:

```mermaid
sequenceDiagram
    participant C as Client
    participant T as A2A Transport<br/>(JSON-RPC)
    participant S as A2A Server<br/>(SDK Core)
    participant E as AgentExecutor<br/>(Your Code)
    participant L as LLM + Tools<br/>(LangChain4j)

    C->>T: POST /<br/>{"method": "SendMessage", "params": {"message": ...}}
    T->>S: Deserialize JSON-RPC → RequestContext
    S->>S: Create Task (status: submitted)
    S->>E: execute(context, emitter)
    E->>E: Extract text from message parts
    E->>E: emitter.startWork() → Task status: working

    E->>L: scheduleService.chat("What AI sessions today?")
    L->>L: LLM decides to call @Tool searchSessions("AI")
    L-->>E: "Found 3 sessions matching 'AI'..."

    E->>S: emitter.addArtifact([TextPart(response)])
    E->>S: emitter.complete() → Task status: completed
    S->>T: Serialize Task → JSON-RPC response
    T->>C: {"result": {"status": "completed", "artifacts": [...]}}
```

### Sequence 2: Concierge Multi-Tenant Runtime

Exercise 5 runs the four specialist agents as tenants in one Concierge runtime. The Orchestrator's A2A clients are configured with the corresponding tenant AgentCards:

```mermaid
sequenceDiagram
    participant O as Orchestrator<br/>:8090
    participant C as Concierge tenants<br/>Quarkus :8080

    Note over C: Schedule, Travel, Venue, Expense
    O->>C: A2A request via Schedule AgentCard
    C-->>O: Schedule agent response
    O->>C: A2A request via Travel AgentCard
    C-->>O: Travel agent response
    O->>C: A2A request via Expense AgentCard
    C-->>O: Expense agent response
```

### Sequence 3: Maya's Full Scenario (Orchestrated Multi-Agent)

This is the complete flow when Maya sends her complex query:

```mermaid
sequenceDiagram
    participant M as Maya
    participant O as Orchestrator<br/>:8090
    participant S as Schedule tenant<br/>Concierge :8080
    participant T as Travel tenant<br/>Concierge :8080
    participant X as Expense tenant<br/>Concierge :8080

    M->>O: REST POST /api/query<br/>"My flight was delayed... what talks today, how to get to venue, log my taxi receipt?"

    Note over O: Step 1: Supervisor selects configured agents

    Note over O: Step 2: Dispatch to specialists
    O->>S: A2A JSON-RPC<br/>"AI sessions later today"
    O->>T: A2A JSON-RPC<br/>"fastest route from airport to venue"
    O->>X: A2A JSON-RPC<br/>"process taxi receipt"

    S-->>O: Relevant session recommendations for Day 1
    T-->>O: "Rideshare: 35 min, €45<br/>Train: disrupted, 55 min, €12"
    X-->>O: Requests the receipt details needed to log the expense

    Note over O: Step 3: Supervisor summarizes agent responses

    O->>M: REST response with the supervisor's summary
```

### Sequence 4: Expense Agent (Exercise 4 — Expense Logging)

The Expense Agent uses LangChain4j tools to validate and log expenses:

```mermaid
sequenceDiagram
    participant C as A2A Client
    participant E as Expense Agent<br/>WildFly :8082
    participant L as LangChain4j + OpenAI
    participant T as ExpenseTool

    C->>E: POST / SendMessage
    E->>E: AgentExecutor.execute()
    E->>E: Submit task (status: submitted)
    E->>E: Start work (status: working)
    E->>L: expenseService.chat(userText)
    L->>T: logExpense() checks amount and policy
    T-->>L: Expense confirmation
    L-->>E: Agent response
    E->>E: Add artifact and complete task
    E->>C: Completed Task with expense artifact
```

## Prerequisites

- **JDK 21+** (e.g., Temurin, GraalVM)
- **Maven 3.9+**
- **Python 3.11+** with `pip` or `uv`
- **Podman** with `podman-compose`
- **curl** or **httpie** for testing
- A terminal with at least 4 tabs/panes
- An OpenAI API key with API billing enabled. ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.

## Quick Start

```bash
# 1. Clone the repo
git clone <repo-url>
cd a2a-agent-lab-2026

# 2. Start infrastructure (PostgreSQL, Kafka, and Grafana LGTM)
podman-compose -f exercises/exercise-5-orchestrator/podman-compose.yml up -d

# 3. Set your OpenAI API key (used with gpt-6-luna at medium reasoning effort)
export OPENAI_API_KEY=your-api-key-here

# 4. Verify
open http://localhost:3000                     # Grafana UI (admin/admin)
```

## Exercises

| # | Exercise | Time | What You Build | Runtime |
|---|----------|------|----------------|---------|
| 1 | [Your First A2A Agent](exercises/exercise-1-schedule-advisor/) | 30 min | Schedule & Content Advisor | Quarkus + `@RegisterAiService` |
| 2 | [Cross-Runtime Agents](exercises/exercise-2-venue-agent/) | 20 min | Venue & On-Site Operations | Spring Boot + LangChain4j |
| 3 | [Cross-Language Interop](exercises/exercise-3-travel-agent/) | 15 min | Travel & Logistics Agent | Python A2A SDK |
| 4 | [Expense & Compliance Agent](exercises/exercise-4-expense-agent/) | 20 min | Expense & Compliance Agent | WildFly 41 (Jakarta EE) |
| 5 | [The Orchestrator](exercises/exercise-5-orchestrator/) | 25 min | Orchestrator and multi-tenant Concierge | Quarkus + A2A |
| 6 | [Enterprise A2A (Bonus)](exercises/exercise-6-enterprise/) | Bonus | Shared task store and cross-node Kafka replication | Two WildFly 41 instances |

Each exercise adds a new agent to the DevSphere mesh. If you fall behind, check the `solutions/` directory for complete working code at each checkpoint.

## The Agents

### The Orchestrator & Concierge (Quarkus)
The Orchestrator is a Quarkus REST gateway at `:8090`. Its LangChain4j supervisor calls four configured A2A clients. Those clients target the Schedule, Travel, Venue, and Expense tenants in the Quarkus Concierge runtime at `:8080`.

### The Schedule & Content Advisor (Quarkus)
Deep-scans the summit's session catalog, speaker names and session details, and domain tracks. Matches attendee skill levels and interests to specific talks. Uses Quarkus LangChain4j's `@RegisterAiService` with `@Tool`-annotated CDI beans for native LLM tool calling.

### The Travel & Logistics Agent (Python A2A SDK)
Uses sample flight, transit, and hotel data. Handles travel queries, flight disruption information, and commute routes to the venue. Built with the Python A2A SDK reference implementation.

### The Venue & On-Site Operations Agent (Spring Boot + LangChain4j)
Robust enterprise microservice that manages real-time IoT room capacity sensors, indoor interactive mapping, and catering queue tracking.

### The Expense & Compliance Agent (WildFly 41 + Jakarta EE)
Standardizes receipts into corporate audit-ready expense logs. Deployed on WildFly 41 and uses LangChain4j with OpenAI to process expense requests. Supports JSON-RPC and REST A2A transports.

## Building

All Java exercises can be compiled at once from the root:

```bash
mvn compile
```

Or from the exercises directory:

```bash
cd exercises
mvn compile
```

## Key Technologies

- **[A2A Protocol](https://google.github.io/A2A/)** — Open standard for agent-to-agent communication
- **[A2A Java SDK](https://github.com/a2aproject/a2a-java)** — Java implementation of the A2A protocol
- **[LangChain4j](https://docs.langchain4j.dev/)** — Java framework for LLM-powered applications
- **[A2A Jakarta EE SDK](https://github.com/wildfly-extras/a2a-jakarta)** — A2A integration for Jakarta EE / WildFly
- **[Quarkus](https://quarkus.io/)** — Supersonic Subatomic Java framework
- **[Spring Boot](https://spring.io/projects/spring-boot)** — Java application framework
- **[WildFly](https://www.wildfly.org/)** — Jakarta EE application server
- **[OpenAI API](https://developers.openai.com/api/docs/models/gpt-6-luna)** — GPT-6 Luna via the Responses API
- **[OpenTelemetry](https://opentelemetry.io/)** — Observability framework
- **[Grafana LGTM](https://grafana.com/blog/2024/03/13/an-opentelemetry-backend-in-a-docker-image-introducing-grafana/otel-lgtm/)** — All-in-one observability stack (Loki + Grafana + Tempo + Mimir)
