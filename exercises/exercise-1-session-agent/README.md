# Exercise 1: Your First A2A Agent — The Session Recommender

**Time:** 30 minutes

The conference organizers need an AI agent that helps attendees find the right sessions to attend — you're building it.

## What You Build

- A **Quarkus + LangChain4j** A2A agent running on port 8080
- An **AgentCard** advertising the agent's skills via `/.well-known/agent.json`
- An **AgentExecutor** that receives A2A messages and returns structured responses
- LLM-powered **session search** using Ollama/Mistral with tool calling against the conference schedule

## Key A2A Concepts

| Concept | What It Is |
|---------|-----------|
| **AgentCard** | JSON metadata at `/.well-known/agent.json` — the agent's machine-readable business card (name, skills, capabilities, endpoint) |
| **Task Lifecycle** | Requests progress through states: `SUBMITTED` → `WORKING` → `COMPLETED` (or `FAILED`) |
| **Message / Part** | Messages carry a `role` (`USER` or `AGENT`) and an array of typed Parts (`TextPart`, `FilePart`, `DataPart`) |
| **JSON-RPC** | A2A uses JSON-RPC 2.0 — the key method is `message/send` |

## Project Structure

```
src/main/java/dev/devconf/session/
├── SessionTool.java                 # @Tool methods: searchSessions, listTracks, getScheduleByDate
├── SessionService.java              # LangChain4j AI Service interface (@RegisterAiService)
├── SessionAgentCardProducer.java    # CDI producer for the AgentCard (@PublicAgentCard)
└── SessionAgentExecutorProducer.java # CDI producer for the AgentExecutor (message handling)

src/main/resources/
└── application.properties           # Ollama URL, model, port, agent metadata
```

## Prerequisites

Ollama must be running with the `mistral` model pulled. From the repo root:

```bash
podman-compose up -d
podman logs -f devconf-ollama-pull   # Wait for "success"
```

Verify: `curl http://localhost:11434/api/tags` should list `mistral`.

## How to Run

```bash
cd exercise-1-session-agent
mvn quarkus:dev
```

### Test the AgentCard

```bash
curl -s http://localhost:8080/.well-known/agent.json | python3 -m json.tool
```

### Send a message

```bash
curl -s -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "message/send",
    "params": {
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "What AI sessions are available?"}]
      }
    },
    "id": "1"
  }' | python3 -m json.tool
```

## How to Verify

A successful response looks like:

```json
{
  "jsonrpc": "2.0",
  "id": "1",
  "result": {
    "id": "<task-id>",
    "status": {
      "state": "completed"
    },
    "artifacts": [
      {
        "parts": [
          {
            "type": "text",
            "text": "Here are the AI-related sessions at DevConf 2026..."
          }
        ]
      }
    ]
  }
}
```

- `status.state` is `completed`
- `artifacts[0].parts[0].text` contains session recommendations

## Step-by-Step Guide

See [../../docs/exercise-1.md](../../docs/exercise-1.md) for the full walkthrough.
