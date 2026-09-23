# Solution: Exercise 5 — Orchestrator & Concierge (Quarkus)

The complete working **Orchestrator & Concierge** — a dynamic router that discovers agents, decomposes complex queries into sub-tasks, dispatches them to specialist agents, and aggregates the results.

## What's Included

The full source lives in `../../exercises/exercise-5-orchestrator/`:

| File | Purpose |
|------|---------|
| `AgentDiscoveryService.java` | Fetches AgentCards from configured URLs, builds agent registry |
| `SubTask.java` | Record: `(agentName, query)` — one sub-task targeting one agent |
| `QueryDecomposer.java` | LangChain4j AI Service that splits queries into `SubTask`s |
| `ResponseAggregator.java` | LangChain4j AI Service that synthesizes multi-agent responses |
| `OrchestratorAgentCardProducer.java` | AgentCard with skill "general-conference-assistant" |
| `OrchestratorAgentExecutorProducer.java` | Orchestration loop: decompose → dispatch → aggregate |
| `application.properties` | Port 8090, agent URLs, OpenAI model and reasoning config |

## Prerequisites

- `OPENAI_API_KEY` environment variable set with an OpenAI API key with API billing enabled for `gpt-6-luna` through the Responses API at medium reasoning effort. ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.
- **Schedule & Content Advisor** running on port **8080** (Exercise 1)
- **Venue & On-Site Operations Agent** running on port **8081** (Exercise 2)
- **Travel & Logistics Agent** running on port **9000** (Exercise 3)

## How to Run

```bash
export OPENAI_API_KEY=your-api-key-here
./run.sh
# Or manually:
cd ../../exercises/exercise-5-orchestrator
mvn quarkus:dev
```

The Orchestrator starts on **port 8090**.

## How to Verify

```bash
# Maya's full scenario
curl -s --max-time 120 -X POST http://localhost:8090/ \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "msg-1",
        "role": "ROLE_USER",
        "parts": [{"text": "My flight was delayed so I missed the morning shuttle. I'\''m interested in agentic AI and Java agents—what talks should I catch today, how do I get to the venue quickly, and can you log my taxi receipt?"}]
      }
    },
    "id": "1"
  }' | python3 -m json.tool
```
