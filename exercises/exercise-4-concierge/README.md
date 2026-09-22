# Exercise 4: The Concierge — Orchestrating the Agent Mesh

**Time:** 25 minutes

> _An attendee asks: "I'm arriving Thursday afternoon — what sessions should I attend, and where should I eat nearby afterward?" No single agent can answer this. You need an orchestrator._

## What You Build

A **Concierge Agent** (Quarkus + LangChain4j) that dynamically discovers other agents, decomposes complex queries into sub-tasks, dispatches them to specialist agents via A2A, and aggregates the responses into a single coherent answer.

## Architecture

```
                  ┌─────────────────────────────┐
  User query ───► │    Concierge Agent (:8090)   │
                  │                             │
                  │  1. Decompose (LLM)         │
                  │     → SubTask[sessions]      │
                  │     → SubTask[dining]        │
                  │                             │
                  │  2. Dispatch via A2A         │
                  │     ┌────────┐ ┌──────────┐ │
                  │     │Session │ │  Travel  │ │
                  │     │ :8080  │ │  :9000   │ │
                  │     └────────┘ └──────────┘ │
                  │                             │
                  │  3. Aggregate (LLM)         │
                  └──────────────┬──────────────┘
                                 │
                  Final answer ◄─┘
```

## Project Structure

```
exercise-4-concierge/
├── pom.xml
└── src/main/java/dev/devconf/concierge/
    ├── AgentDiscoveryService.java        # Discovers agents via AgentCard HTTP fetch
    ├── QueryDecomposer.java              # LLM-powered query decomposition into SubTasks
    ├── ResponseAggregator.java           # LLM-powered response synthesis
    ├── ConciergeAgentCardProducer.java   # CDI producer for the Concierge AgentCard
    ├── ConciergeAgentExecutorProducer.java  # Orchestration loop: decompose → dispatch → aggregate
    └── SubTask.java                      # record(agentName, query)
```

## Prerequisites

- Exercise 1 **Session Agent** running on port **8080**
- Exercise 3 **Travel Tips Agent** running on port **9000**
- Ollama running on port **11434**

## How to Run

```bash
cd exercise-4-concierge
mvn quarkus:dev
```

The Concierge starts on **http://localhost:8090**.

## How to Verify

**1. Check the AgentCard:**

```bash
curl http://localhost:8090/.well-known/agent-card.json | python3 -m json.tool
```

**2. Send a complex, multi-domain query:**

```bash
curl -s --max-time 120 -X POST http://localhost:8090 \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "msg-1",
        "role": "ROLE_USER",
        "parts": [{"text": "I am a Java developer arriving Thursday. What sessions should I attend and where should I have dinner afterward?"}]
      }
    },
    "id": "1"
  }' | python3 -m json.tool
```

The response should combine session recommendations **and** restaurant suggestions — pulled from two different agents.

## Full Instructions

See [../../docs/exercise-4.md](../../docs/exercise-4.md) for the complete step-by-step guide.
