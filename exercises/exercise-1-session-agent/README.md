# Exercise 1: Your First A2A Agent — The Session Recommender

**Time:** 30 minutes

The conference organizers need an AI agent that helps attendees find the right sessions to attend — you're building it.

## What You Build

- A **Quarkus + LangChain4j** A2A agent running on port 8080
- An **AgentCard** advertising the agent's skills via `/.well-known/agent-card.json`
- An **AgentExecutor** that receives A2A messages and returns structured responses
- LLM-powered **session search** using OpenAI GPT-6 Luna through the Responses API with medium reasoning effort and tool calling against the conference schedule

## Key A2A Concepts

| Concept | What It Is |
|---------|-----------|
| **AgentCard** | JSON metadata at `/.well-known/agent-card.json` — the agent's machine-readable business card (name, skills, capabilities, endpoint) |
| **Task Lifecycle** | Requests progress through states: `SUBMITTED` → `WORKING` → `COMPLETED` (or `FAILED`) |
| **Message / Part** | Messages carry a `role` (`USER` or `AGENT`) and an array of typed Parts (`TextPart`, `FilePart`, `DataPart`) |
| **JSON-RPC** | A2A uses JSON-RPC 2.0 — the key method is `SendMessage` |

## Project Structure

```
src/main/java/dev/devconf/session/
├── SessionTool.java                 # @Tool methods: searchSessions, listTracks, getScheduleByDate
├── SessionService.java              # LangChain4j AI Service interface (@RegisterAiService)
├── SessionAgentCardProducer.java    # CDI producer for the AgentCard (@PublicAgentCard)
└── SessionAgentExecutorProducer.java # CDI producer for the AgentExecutor (message handling)

src/main/resources/
└── application.properties           # OpenAI API key, model, reasoning effort, port, agent metadata
```

## Prerequisites

Set an OpenAI API key with API billing enabled. ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.

```bash
export OPENAI_API_KEY=your-api-key-here
```

## How to Run

```bash
# From the repository root:
cd exercises/exercise-1-session-agent
mvn quarkus:dev
```

### Test the AgentCard

```bash
curl -s http://localhost:8080/.well-known/agent-card.json | python3 -m json.tool
```

### Send a message

```bash
curl -s -X POST http://localhost:8080 \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "msg-1",
        "role": "ROLE_USER",
        "parts": [{"text": "What agentic AI sessions are available on October 7?"}]
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
    "task": {
      "id": "<task-id>",
      "status": {
        "state": "TASK_STATE_COMPLETED"
      },
      "artifacts": [
        {
          "parts": [
            {
              "text": "Here are the AI-related sessions at DevConf 2026..."
            }
          ]
        }
      ]
    }
  }
}
```

- `result.task.status.state` is `TASK_STATE_COMPLETED`
- `result.task.artifacts[0].parts[0].text` contains session recommendations

## Companion Project

The companion Schedule & Content Advisor project is in [exercise-1-schedule-advisor](../exercise-1-schedule-advisor/README.md).

## Full Instructions

See [../../docs/exercise-1.md](../../docs/exercise-1.md) for the Exercise 1 walkthrough. It covers the companion Schedule & Content Advisor project, not this Session Recommender.
