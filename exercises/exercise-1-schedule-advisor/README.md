# Exercise 1: Schedule & Content Advisor (Quarkus)

The **Schedule & Content Advisor** deep-scans the Devoxx Belgium 2026 session catalog, speaker bios, and domain tracks. It matches attendee skill levels and interests to specific talks.

Built with **Quarkus**, the **A2A Java SDK reference implementation** (`a2a-java-sdk-reference-jsonrpc`), and **Quarkus LangChain4j** with Google AI Gemini for native `@RegisterAiService` tool calling.

## Quick Start

```bash
# Start in dev mode (hot-reload on port 8080)
mvn quarkus:dev

# Or build and run the jar
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

## Verify

```bash
# Fetch the AgentCard
curl -s http://localhost:8080/.well-known/agent-card.json | jq .

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
        "parts": [{"text": "I arrive at 9:45 AM on 2026-10-07 and I am interested in agentic AI and Java agents. What talks should I catch today?"}]
      }
    },
    "id": "test-1"
  }' | jq .

# If the response shows "state": "TASK_STATE_WORKING", the agent is still processing.
# Extract the task ID and poll with GetTask until it completes:
TASK_ID="<task-id-from-response>"   # e.g. "af4e2602-dd27-4702-aa7f-752198cef538"

curl -s -X POST http://localhost:8080/ \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d "{
    \"jsonrpc\": \"2.0\",
    \"method\": \"GetTask\",
    \"params\": {
      \"id\": \"$TASK_ID\",
      \"historyLength\": 10
    },
    \"id\": \"get-task-1\"
  }" | jq .
```

## Key Files

| File | Purpose |
|------|---------|
| `ScheduleTool.java` | `@ApplicationScoped` CDI bean with LangChain4j `@Tool` methods for searching sessions, filtering by time, speaker info |
| `ScheduleService.java` | `@RegisterAiService(tools = ScheduleTool.class)` — Quarkus auto-wires the LLM with the schedule tools |
| `ScheduleAgentCardProducer.java` | CDI `@Produces @PublicAgentCard` for the A2A AgentCard |
| `ScheduleAgentExecutorProducer.java` | CDI `@Produces` for the AgentExecutor (message handling) |
| `application.properties` | Quarkus HTTP port, Gemini config, session data path, agent identity |

## How It Works

```
User message → A2A JSON-RPC transport
  → AgentExecutor.execute()
    → ScheduleService.chat(userText)     ← @RegisterAiService
      → LLM decides which @Tool to call
      → ScheduleTool.searchSessions()    ← CDI bean with @Tool
      → LLM formulates response
    → emitter.addArtifact(response)
    → emitter.complete()
  → JSON-RPC response with artifacts
```

## Skills

| Skill ID | Description |
|----------|-------------|
| `session-search` | Search sessions by topic, technology, speaker, or track |
| `session-recommend` | Personalized recommendations based on interests and availability |
| `speaker-info` | Speaker information and their sessions |
