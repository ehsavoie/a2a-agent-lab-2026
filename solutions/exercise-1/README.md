# Solution: Exercise 1 — Schedule & Content Advisor (Quarkus)

This is the complete working **Schedule & Content Advisor** agent built with Quarkus, the A2A Java SDK reference implementation, and Quarkus LangChain4j with `@RegisterAiService` for native LLM tool calling.

## What's Included

| File | Purpose |
|------|---------|
| `ScheduleTool.java` | `@ApplicationScoped` CDI bean with LangChain4j `@Tool` methods for searching sessions, speakers, and filtering by time |
| `ScheduleService.java` | `@RegisterAiService(tools = ScheduleTool.class)` — Quarkus auto-wires the LLM with the schedule tools |
| `ScheduleAgentCardProducer.java` | CDI producer for the A2A AgentCard |
| `ScheduleAgentExecutorProducer.java` | CDI producer for the AgentExecutor |
| `application.properties` | Quarkus HTTP port, OpenAI model and reasoning config, session data path, agent identity |

## Prerequisites

- `OPENAI_API_KEY` environment variable set with an OpenAI API key with API billing enabled for `gpt-6-luna` through the Responses API at medium reasoning effort. ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.
- JDK 21+, Maven 3.9+

## How to Run

```bash
export OPENAI_API_KEY=your-api-key-here
./run.sh
# Or manually:
cd ../../exercises/exercise-1-schedule-advisor
mvn quarkus:dev
```

## How to Verify

```bash
# Check the AgentCard
curl -s http://localhost:8080/.well-known/agent-card.json | python3 -m json.tool

# Send a message
curl -s -X POST http://localhost:8080/ \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "msg-1",
        "role": "ROLE_USER",
        "parts": [{"text": "What sessions about agentic AI are available on October 7?"}]
      }
    },
    "id": "1"
  }' | python3 -m json.tool
```
