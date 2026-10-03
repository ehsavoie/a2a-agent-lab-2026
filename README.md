# Building the DevSphere Concierge — A Multi-Agent A2A Ecosystem

A 2-hour hands-on lab that takes you from zero to a fully orchestrated, multi-runtime, multi-language agent mesh using the **A2A protocol**, **LangChain4j**, and the **A2A Java SDK**.

## The Story

DevConf 2026 is next week and 5,000 attendees need help navigating the conference. You're building **DevSphere** — an AI-powered concierge mesh of specialized agents that collaborate via A2A to answer any attendee question, from session recommendations to travel logistics to expense reporting.

### The Scenario: "The Flight Delay & Keynote Clash"

It is 8:15 AM on Day 1 (October 7, 2026). Maya lands at Brussels Airport, but her flight was delayed by two hours. She opens her DevSphere app and types:

> *"My flight was delayed so I missed the morning shuttle. I'm interested in agentic AI and Java agents — what talks should I catch today, how do I get to Kinepolis Antwerp quickly, and can you log my taxi receipt?"*

Here's how the mesh resolves her request in real-time:

1. **Orchestration** — The Quarkus Orchestrator receives Maya's prompt and its LLM supervisor decides which specialist agents to call
2. **Travel & Transit** — The Concierge Travel tenant compares transit options (Bolt rideshare: 35 min, €45 vs. disrupted NMBS train: 55 min, €12) and recommends the rideshare
3. **Session Matching** — The Concierge Schedule tenant filters sessions on 2026-10-07 for Maya's interests and arrival time
4. **Venue Check** — The Concierge Venue tenant checks real-time IoT room capacity for each suggested session
5. **Expense Log** — The Concierge Expense tenant validates Maya's €45 taxi fare against compliance rules and logs an audit-ready entry

Each exercise adds a new agent to the mesh. By the end, you'll have the complete DevSphere system.

## Architecture

```
                              User (Maya)
                                  |
                       ┌──────────▼──────────┐
                       │ Orchestrator        │
                       │ Quarkus REST :8090  │
                       │ @SupervisorAgent    │
                       └──────────┬──────────┘
                                  │ A2A JSON-RPC
                       ┌──────────▼──────────┐
                       │ Concierge           │
                       │ Quarkus :8080       │
                       ├─────────────────────┤
                       │ /.well-known/       │
                       │   schedule/         │ ← Exercise 1 (standalone) then Exercise 5 (tenant)
                       │   venue/            │ ← Exercise 2 (standalone) then Exercise 5 (tenant)
                       │   travel/           │ ← Exercise 3 (standalone) then Exercise 5 (tenant)
                       │   expense/          │ ← Exercise 4 (standalone) then Exercise 5 (tenant)
                       └─────────────────────┘
```

| Port | Agent / Service | Runtime | A2A transport |
|------|-----------------|---------|---------------|
| 8080 | Schedule Advisor (Ex. 1) or Concierge tenants (Ex. 5) | Quarkus | JSON-RPC |
| 8081 | Venue & On-Site Operations (Ex. 2) | Spring Boot | REST |
| 9000 | Travel & Logistics (Ex. 3) | Python | REST |
| 8082 | Expense & Compliance Agent (Ex. 4) | WildFly 41 | JSON-RPC + REST |
| 8083 | Expense Client web UI (Ex. 4) | WildFly 41 | JAX-RS |
| 8090 | Orchestrator REST API (Ex. 5) | Quarkus | — |
| 8080 / 9080 | Conference Feedback Agent Node A / Node B (Ex. 6) | WildFly 41 | JSON-RPC |

For Exercise 5, stop the standalone agents from Exercises 1–4 and run the Concierge (all four tenants on port 8080) alongside the Orchestrator (port 8090).

## How It All Works

### Sequence 1: Single Agent Request

When a client sends a message to any agent:

```
Client
  │
  │  POST /  {"method": "SendMessage", "params": {"message": ...}}
  ▼
A2A Transport (JSON-RPC)
  │  Deserialize → RequestContext
  ▼
A2A Server (SDK Core)
  │  Create Task (state: SUBMITTED)
  ▼
AgentExecutor (your code)
  │  extract text from message parts
  │  emitter.startWork()          → Task state: WORKING
  │  scheduleService.chat(...)    → LLM + tool calls
  │  emitter.addArtifact(...)     → attach response
  │  emitter.complete()           → Task state: COMPLETED
  ▼
Client receives {"result": {"status": "completed", "artifacts": [...]}}
```

### Sequence 2: Maya's Full Scenario (Orchestrated Multi-Agent)

```
Maya
  │  POST /api/query
  │  "My flight was delayed... what talks today, how to get to venue, log my taxi receipt?"
  ▼
Orchestrator :8090  (@SupervisorAgent — LLM decides which agents to call)
  │
  ├──► Schedule tenant :8080  →  sessions on 2026-10-07 about agentic AI and Java
  ├──► Travel tenant :8080    →  Bolt 35 min €45 (recommended) / NMBS 55 min €12 (disrupted)
  ├──► Venue tenant :8080     →  room capacity for each suggested session
  └──► Expense tenant :8080   →  log €45 taxi, validate against compliance rules
  │
  ▼
Unified response synthesized by the LLM supervisor
```

## Prerequisites

- **JDK 21+** (e.g., Temurin, GraalVM)
- **Maven 3.9+**
- **Python 3.11+** with `pip` or `uv`
- **Podman / Docker** — PostgreSQL and Kafka for Exercise 6 (bonus only)
- **curl** for API testing
- **IDE** — IntelliJ IDEA, VS Code, etc.
- An OpenAI API key with API billing enabled. A ChatGPT subscription does not cover API usage.

## Quick Start

```bash
# 1. Clone the repo
git clone https://github.com/ehsavoie/a2a-agent-lab-2026
cd a2a-agent-lab-2026

# 2. Set your OpenAI API key
export OPENAI_API_KEY=your-api-key-here

# 3. Build all Java modules
mvn compile

# 4. Start Exercise 1
cd exercises/exercise-1-schedule-advisor
mvn quarkus:dev
```

Exercise 6 (bonus) additionally requires PostgreSQL and Kafka — see the [Exercise 6 prerequisites](#exercise-6-load-balanced-a2a-bonus) section below.

## Exercises

| # | Exercise | Time | What You Build | Runtime |
|---|----------|------|----------------|---------|
| 1 | [Your First A2A Agent](exercises/exercise-1-schedule-advisor/) | 30 min | Schedule & Content Advisor — AgentCard, AgentExecutor, `@RegisterAiService` tool calling | Quarkus + A2A Java SDK |
| 2 | [Cross-Runtime Agents](exercises/exercise-2-venue-agent/) | 20 min | Venue & On-Site Operations — IoT room capacity, indoor navigation, fast-track entry passes | Spring Boot + spring-a2a + LangChain4j |
| 3 | [Cross-Language Interop](exercises/exercise-3-travel-agent/) | 15 min | Travel & Logistics Agent — transit comparison, receipt extraction, Java ↔ Python interop | Python A2A SDK |
| 4 | [Expense & Compliance Agent](exercises/exercise-4-expense-agent/) | 20 min | Expense Agent + JAX-RS client — compliance validation, cross-agent receipt handoff from Travel Agent | WildFly 41 + Jakarta EE |
| 5 | [The Orchestrator](exercises/exercise-5-orchestrator/) | 25 min | Multi-tenant Concierge + Orchestrator — `@SupervisorAgent`, `@A2AClientAgent`, `@Tenant`, web UI | Quarkus + A2A Java SDK |
| 6 | [Load-Balanced A2A (Bonus)](exercises/exercise-6-enterprise/) | Bonus | Conference Feedback Agent on two WildFly nodes — shared PostgreSQL task store, Kafka event replication | WildFly 41 × 2 + PostgreSQL + Kafka |

Each exercise adds a new agent to the DevSphere mesh. If you fall behind, check the `solutions/` directory for complete working code.

## The Agents

### Schedule & Content Advisor (Quarkus — port 8080)
Answers questions about sessions, speakers, tracks, and timing. Uses Quarkus LangChain4j `@RegisterAiService` with `@Tool`-annotated CDI beans to search the real conference schedule. Key tool: `filterSessionsAfterTime` — lets the LLM exclude sessions Maya already missed.

### Venue & On-Site Operations (Spring Boot — port 8081)
Manages real-time IoT room capacity sensors, indoor navigation, catering queue tracking, and fast-track entry pass reservation. Built with [spring-a2a](https://github.com/Sh1bari/spring-a2a) + LangChain4j using Spring `@Bean` wiring instead of Quarkus `@Produces`.

### Travel & Logistics Agent (Python — port 9000)
Rule-based (no LLM) agent built with the Python A2A SDK. Compares transit options from Brussels Airport to Kinepolis Antwerp, checks flight status, and extracts structured receipt data for the Expense Agent. Requires no OpenAI key.

### Expense & Compliance Agent (WildFly 41 — port 8082)
Validates and logs expense entries against corporate compliance rules (€75 meal limit, €200 transport limit). Uses LangChain4j with the OpenAI Responses API. Supports both JSON-RPC and REST A2A transports. A JAX-RS web client (port 8083) provides a browser UI.

### Orchestrator & Concierge (Quarkus — ports 8080 + 8090)
The Concierge bundles all four specialist agents as tenants in one Quarkus runtime using `@Tenant` CDI qualifiers and `a2a-java-extras-multitenancy`. The Orchestrator is a Quarkus REST gateway whose `@SupervisorAgent` LLM autonomously decides which `@A2AClientAgent` sub-agents to call and synthesizes a unified response. Web UI at `http://localhost:8090`.

### Conference Feedback Agent (WildFly 41 × 2 — ports 8080 + 9080, bonus)
The same agent WAR deployed on two WildFly nodes. Uses a JPA-backed TaskStore (PostgreSQL) shared across nodes and a Kafka-replicated queue manager so any node can serve any request. Demo: a client creates a task on Node A, subscribes via Node B, and receives `WORKING → COMPLETED` events via Kafka.

## Building

All Java exercises can be compiled at once from the root:

```bash
mvn compile
```

Or run a single exercise in dev mode:

```bash
cd exercises/exercise-1-schedule-advisor
mvn quarkus:dev
```

### Exercise 6: Load-Balanced A2A (Bonus)

Exercise 6 requires PostgreSQL and Kafka. Start them first:

```bash
cd exercises/exercise-6-enterprise
podman-compose up -d
# or: docker compose -f podman-compose.yml up -d
```

Then build and start the two WildFly nodes (see the workshop guide for the full startup commands with port offsets and Kafka consumer group IDs).

## Key Technologies

- **[A2A Protocol](https://google.github.io/A2A/)** — Open standard for agent-to-agent communication
- **[A2A Java SDK](https://github.com/a2aproject/a2a-java)** — Java implementation of the A2A protocol
- **[A2A Python SDK](https://github.com/a2aproject/a2a-python)** — Python implementation of the A2A protocol
- **[LangChain4j](https://docs.langchain4j.dev/)** — Java framework for LLM-powered applications
- **[A2A Jakarta EE SDK](https://github.com/wildfly-extras/a2a-jakarta)** — A2A integration for Jakarta EE / WildFly
- **[spring-a2a](https://github.com/Sh1bari/spring-a2a)** — Spring Boot A2A server support
- **[Quarkus](https://quarkus.io/)** — Supersonic Subatomic Java
- **[Spring Boot](https://spring.io/projects/spring-boot)** — Java application framework
- **[WildFly](https://www.wildfly.org/)** — Jakarta EE application server
- **[OpenAI API](https://platform.openai.com/)** — GPT-6 Luna via the Responses API (used in Exercises 1, 2, 4, 5)
