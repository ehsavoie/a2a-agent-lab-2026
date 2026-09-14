# Solution: Exercise 1 — Session Agent (Quarkus)

This is the complete working **Session Recommender Agent** built with Quarkus and LangChain4j.

## What's Included

The full source lives in `../../exercises/exercise-1-session-agent/`:

| File | Purpose |
|------|---------|
| `SessionTool.java` | LangChain4j `@Tool` methods for searching sessions by keyword, track, or date |
| `SessionService.java` | `@RegisterAiService` interface wiring the LLM with the session tools |
| `SessionAgentCardProducer.java` | CDI producer for the A2A AgentCard (skills: session-search, session-recommend) |
| `SessionAgentExecutorProducer.java` | CDI producer for the AgentExecutor (message → LLM → response) |
| `application.properties` | Ollama connection, agent metadata, server port (8080) |

## Prerequisites

- Ollama running on `localhost:11434` with the `mistral` model pulled
- JDK 21+, Maven 3.9+

```bash
# If you haven't started infrastructure yet:
cd ../../ && podman-compose up -d
```

## How to Run

```bash
./run.sh
# Or manually:
cd ../../exercises/exercise-1-session-agent
mvn quarkus:dev
```

## How to Verify

**1. Check the AgentCard:**

```bash
curl -s http://localhost:8080/.well-known/agent.json | python3 -m json.tool
```

You should see a JSON document with:
- `name`: "Session Recommender Agent"
- `skills`: two entries — "session-search" and "session-recommend"
- `capabilities`: streaming false, pushNotifications false

**2. Send a message:**

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

You should see a JSON-RPC response containing a `result` with:
- A `status` object with `state`: `"completed"`
- An `artifacts` array with a `TextPart` containing session recommendations
