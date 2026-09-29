# Exercise 5: The Orchestrator & Concierge (Quarkus)

Exercise 5 uses two Quarkus services: the Orchestrator on port **8090** and the multi-tenant Concierge runtime on port **8080**. Concierge hosts the Schedule, Travel, Venue, and Expense agents. The Exercise 1–4 standalone services are not needed for this exercise.

## Quick Start

```bash
cd exercises/exercise-5-orchestrator && mvn -pl concierge quarkus:dev
```

Start the Orchestrator in another terminal:

```bash
cd exercises/exercise-5-orchestrator && mvn -pl orchestrator quarkus:dev
```

Both services need `OPENAI_API_KEY` set in their terminal. The Orchestrator accepts requests on **port 8090**.

## Test

```bash
curl -s -X POST http://localhost:8090/api/query \
  -H "Content-Type: application/json" \
  -d '{
    "query": "My flight was delayed so I missed the morning shuttle. I am interested in agentic AI and Java agents. What talks should I catch today, how do I get to the venue quickly, and can you log my taxi receipt?"
  }' | jq .
```

## Companion Project

The companion Concierge module is in [concierge](concierge/README.md).

## Full Instructions

See [../../docs/exercise-5.md](../../docs/exercise-5.md) for the complete step-by-step guide.
