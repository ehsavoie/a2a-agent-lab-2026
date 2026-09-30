# Exercise 5: Concierge Multi-Tenant Agent Runtime

**Time:** 25 minutes

> _An attendee asks: "I'm arriving Thursday afternoon — what sessions should I attend, and where should I eat nearby afterward?" No single agent can answer this. You need an orchestrator._

## What You Build

The Concierge module is a Quarkus A2A server that hosts four tenant agents: Schedule, Travel, Venue, and Expense. The Orchestrator module calls the tenant agents through their AgentCards.

## Architecture

```
                  ┌─────────────────────────────┐
  User query ───► │    Orchestrator (:8090)      │
                  │    REST /api/query           │
                  └──────────────┬──────────────┘
                                 │ A2A JSON-RPC
                  ┌──────────────▼──────────────┐
                  │ Concierge runtime (:8080)   │
                  │ Schedule · Travel · Venue   │
                  │ Expense tenants             │
                  └─────────────────────────────┘
```

## Project Structure

```
concierge/
├── pom.xml
└── src/main/java/dev/devconf/
    ├── schedule/                          # Schedule tenant
    ├── travel/                            # Travel tenant
    ├── venue/                             # Venue tenant
    ├── expense/                           # Expense tenant
    └── A2ARequestLogger.java
```

## Prerequisites

- Java 21
- `OPENAI_API_KEY` environment variable set for `gpt-6-luna` through the Responses API at medium reasoning effort. An OpenAI API key with API billing enabled is required; ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.

Set `OPENAI_API_KEY` in this terminal before starting Concierge.

## How to Run

```bash
cd exercises/exercise-5-orchestrator && mvn -pl concierge quarkus:dev
```

The Concierge starts on **http://localhost:8080**.

## How to Verify

**1. Check the AgentCard:**

```bash
curl http://localhost:8080/.well-known/schedule/agent-card.json | python3 -m json.tool
curl http://localhost:8080/.well-known/travel/agent-card.json | python3 -m json.tool
curl http://localhost:8080/.well-known/venue/agent-card.json | python3 -m json.tool
curl http://localhost:8080/.well-known/expense/agent-card.json | python3 -m json.tool
```

The Orchestrator's documented `/api/query` interaction exercises calls to the Concierge tenants.

## Companion Project

The companion Orchestrator project is in [the sibling orchestrator module](../README.md).

## Full Instructions

See [../../../docs/exercise-5.md](../../../docs/exercise-5.md) for the complete step-by-step guide.
