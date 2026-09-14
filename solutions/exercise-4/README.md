# Solution: Exercise 4 — Concierge Orchestrator

The complete working **Concierge Agent** — a dynamic orchestrator that discovers agents, decomposes complex queries into sub-tasks, dispatches them to specialist agents, and aggregates the results.

## What's Included

The full source lives in `../../exercises/exercise-4-concierge/`:

| File | Purpose |
|------|---------|
| `AgentDiscoveryService.java` | Fetches AgentCards from configured URLs, builds agent registry |
| `SubTask.java` | Record: `(agentName, query)` — one sub-task targeting one agent |
| `QueryDecomposer.java` | LangChain4j AI Service that splits complex queries into `SubTask`s |
| `ResponseAggregator.java` | LangChain4j AI Service that synthesizes multi-agent responses |
| `ConciergeAgentCardProducer.java` | AgentCard with skill "general-conference-assistant" |
| `ConciergeAgentExecutorProducer.java` | Orchestration loop: decompose → dispatch → aggregate |
| `application.properties` | Port 8090, agent URLs, Ollama config |

## Prerequisites

- Ollama running on `localhost:11434` with the `mistral` model
- **Session Agent** running on port **8080** (Exercise 1)
- **Travel Tips Agent** running on port **9000** (Exercise 3)

## How to Run

Make sure the two specialist agents are running first, then:

```bash
./run.sh
# Or manually:
cd ../../exercises/exercise-4-concierge
mvn quarkus:dev
```

The Concierge starts on **port 8090**.

## How to Verify

**1. Check the AgentCard:**

```bash
curl -s http://localhost:8090/.well-known/agent.json | python3 -m json.tool
```

**2. Send a complex query that requires multiple agents:**

```bash
curl -s --max-time 120 -X POST http://localhost:8090 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "message/send",
    "params": {
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "I am a Java developer arriving Thursday. What sessions should I attend and where should I have dinner afterward?"}]
      }
    },
    "id": "1"
  }' | python3 -m json.tool
```

**What to expect:** The Concierge will:
1. Decompose the query into two sub-tasks (sessions + restaurants)
2. Dispatch "sessions for a Java developer on Thursday" to the **Session Agent**
3. Dispatch "dinner recommendations" to the **Travel Tips Agent**
4. Aggregate both responses into a single coherent answer

The response may take 30-60 seconds (three LLM calls: decompose, session search, aggregate).
