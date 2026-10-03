# A2A Agent Lab — Prerequisites

## Clone the repository

Scan the QR code or visit the link below to access the repository:

[![QR code for the workshop repository](A2A%20Workshop%20Repo.png)](https://github.com/ehsavoie/a2a-agent-lab-2026)

**https://github.com/ehsavoie/a2a-agent-lab-2026**

```bash
git clone https://github.com/ehsavoie/a2a-agent-lab-2026
cd a2a-agent-lab-2026
```

## Required software

| Tool | Version | Purpose |
|------|---------|---------|
| **JDK** | 21+ | Java development |
| **Maven** | 3.9+ | Build tool |
| **Python** | 3.11+ | Exercise 3 (Travel Agent) |
| **Podman / Docker** | any | PostgreSQL and Kafka for Exercise 6 (Enterprise) |
| **OpenAI API key** | API billing enabled | Java LLM exercises |
| **curl** | any | API testing |
| **IDE** | IntelliJ / VS Code / etc. | Code editor |

## 1. Set up your API key

The Java agent examples call the OpenAI API. Set `OPENAI_API_KEY` to an API key with API billing enabled. A ChatGPT subscription does not cover API usage, and the GPT-6 Luna API Free tier is unsupported.

```bash
export OPENAI_API_KEY=your-api-key-here
```

## 2. Start the Exercise 6 infrastructure (bonus exercise only)

Exercise 6 (Load-Balanced A2A) requires PostgreSQL and Kafka to share state across agent instances. Use Podman Compose or Docker Compose from the exercise directory:

```bash
cd exercises/exercise-6-enterprise
podman-compose up -d
# or: docker compose -f podman-compose.yml up -d
```

This starts:
- **PostgreSQL 17** on port `5432` — JPA-backed task store shared across WildFly nodes
- **Kafka 4.1** on port `9092` — replicated queue manager for cross-node task events

Exercises 1–5 do not require Docker or Podman.

## 3. Project structure

```
a2a-agent-lab-2026/
  pom.xml                              ← Root aggregator
  conference-data/sessions.json        ← Shared conference schedule data
  docs/                                ← Step-by-step exercise guides
  exercises/
    exercise-1-schedule-advisor/       ← Quarkus + A2A Java SDK
    exercise-2-venue-agent/            ← Spring Boot + spring-a2a
    exercise-3-travel-agent/           ← Python A2A SDK
      travel_agent.py                  ← Agent implementation
      java-client/                     ← Java interop test client
    exercise-4-expense-agent/          ← WildFly 41 + Jakarta EE
      expense-agent/                   ← A2A agent (LangChain4j + compliance)
      expense-client/                  ← JAX-RS A2A client
    exercise-5-orchestrator/           ← Multi-project Quarkus Orchestrator
      concierge/                       ← Multi-tenant: Schedule + Venue + Travel + Expense
      orchestrator/                    ← @SupervisorAgent + @A2AClientAgent + REST UI
    exercise-6-enterprise/             ← Multi-node WildFly + JPA + Kafka
      node-a/  node-b/                 ← WildFly agent nodes
      server/                          ← Shared server module
      client/                          ← Cross-node test client
      initdb/                          ← PostgreSQL seed scripts
      podman-compose.yml               ← PostgreSQL + Kafka infrastructure
  solutions/                           ← Complete reference solutions
```
