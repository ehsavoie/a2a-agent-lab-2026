# Exercise 1 — Schedule & Content Advisor (30 min)

> *"Maya needs an agent that answers questions about sessions: 'What talks about agentic AI are on October 7?', 'I'm arriving at 9:45 — what can I still catch?', 'What sessions is Mario Fusco speaking at.'"*

Build your **first A2A agent** using **Quarkus**, the **A2A Java SDK reference implementation**, and **Quarkus LangChain4j** (`@RegisterAiService`). By the end, your agent will advertise its capabilities via an AgentCard, accept questions via `SendMessage`, use an LLM with tool calling to search the real conference schedule, and return structured results.

## A2A Concepts

| Concept | Description |
|---|---|
| **AgentCard** | JSON at `/.well-known/agent-card.json` — identity, skills, capabilities, endpoint URL |
| **AgentExecutor** | Handler that receives A2A messages and produces responses via the emitter pattern |
| **Task Lifecycle** | `SUBMITTED → WORKING → COMPLETED / FAILED` with artifacts attached |
| **Message & Parts** | Messages carry `role` (user/agent) and typed `Part` objects: `TextPart`, `FilePart`, `DataPart` |

## What You Will Build

Four source files complete the agent:

1. **`ScheduleTool.java`** — `@ApplicationScoped` CDI bean with LangChain4j `@Tool` methods that give the LLM access to conference data
2. **`ScheduleService.java`** — `@RegisterAiService(tools = ScheduleTool.class)` AI service interface
3. **`ScheduleAgentCardProducer.java`** — CDI `@Produces @PublicAgentCard` method that defines the agent's identity and skills
4. **`ScheduleAgentExecutorProducer.java`** — CDI `@Produces` method that wires the A2A message handler

## Step 1 — Build the ScheduleTool

Open `src/main/java/dev/devconf/schedule/ScheduleTool.java`. It is an `@ApplicationScoped` CDI bean that loads `sessions.json` at startup and exposes five `@Tool`-annotated methods the LLM can call:

| Tool | Purpose |
|---|---|
| `searchSessions(query)` | Search by keyword, track, speaker, or tag |
| `listTracks()` | List all available conference tracks |
| `getScheduleByDate(date)` | Schedule for a specific date (`YYYY-MM-DD`) |
| `filterSessionsAfterTime(date, time)` | Sessions starting at or after a given time (Maya's late-arrival use case) |
| `getSpeakerInfo(speakerName)` | Speaker biography and sessions |

## Step 2 — Build the ScheduleService

Open `src/main/java/dev/devconf/schedule/ScheduleService.java` and define the AI service:

```java
@RegisterAiService(tools = ScheduleTool.class)
public interface ScheduleService {
    @SystemMessage("""
            You are the DevConf 2026 Schedule & Content Advisor.
            You deep-scan the session catalog, speaker bios, and tracks.
            If the attendee mentions arriving late, use filterSessionsAfterTime.
            Always use your search tools rather than making up information.
            """)
    String chat(@UserMessage String userMessage);
}
```

`@RegisterAiService(tools = ScheduleTool.class)` tells Quarkus to auto-wire the LLM (configured in `application.properties`) with the schedule tools. No manual `AiServices.builder()` needed.

## Step 3 — Implement the AgentCard Producer

Open `ScheduleAgentCardProducer.java` and implement `agentCard()`:

1. Use `AgentCard.builder()` with `.name()`, `.description()`, `.version()` from the injected `@ConfigProperty` fields
2. Add `.supportedInterfaces()` with a single `AgentInterface` using `TransportProtocol.JSONRPC.asString()` and the injected `agentUrl`
3. Add `.capabilities(AgentCapabilities.builder().streaming(false).pushNotifications(false))`
4. Add `.skills()` — three skills: `session-search`, `session-recommend`, `speaker-info`
5. Add `.defaultInputModes(List.of("text"))` and `.defaultOutputModes(List.of("text"))`

The `@Produces @PublicAgentCard` annotation tells the A2A Jakarta transport to serve the card at `/.well-known/agent-card.json` automatically.

## Step 4 — Implement the AgentExecutor

Open `ScheduleAgentExecutorProducer.java` and implement `agentExecutor()`. Return an anonymous `AgentExecutor` with:

**`execute(RequestContext context, AgentEmitter emitter)`:**
1. Extract plain text from `context.getMessage().parts()` (collect `TextPart` strings)
2. Call `emitter.startWork()` — task transitions to `WORKING`
3. Call `scheduleService.chat(userText)`
4. On success: `emitter.addArtifact(...)` then `emitter.complete()`
5. On exception: `emitter.addArtifact(...)` with the error message then `emitter.fail()`

**`cancel(RequestContext context, AgentEmitter emitter)`:** throw `new TaskNotCancelableError()`

Task lifecycle flow:
```
Client sends SendMessage
  → execute() called
  → emitter.startWork()        → Task state: WORKING
  → scheduleService.chat()     → LLM + tool calls happen
  → emitter.addArtifact()      → Attach response content
  → emitter.complete()         → Task state: COMPLETED
  → Response returned to client
```

## Quick Start

Requires JDK 21+, Maven 3.9+, and an OpenAI API key with API billing enabled.

```bash
export OPENAI_API_KEY=your-api-key-here
cd exercises/exercise-1-schedule-advisor
mvn quarkus:dev
```

## Verify

```bash
# Fetch the AgentCard
curl -s http://localhost:8080/.well-known/agent-card.json \
  -H "A2A-Version: 1.0" | jq .

# Send a session query
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
    "id": "test-1"
  }' | jq .

# Maya's late-arrival scenario
curl -s -X POST http://localhost:8080/ \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "msg-2",
        "role": "ROLE_USER",
        "parts": [{"text": "I am arriving at 9:45 on 2026-10-07 and interested in agentic AI and Java agents. What can I still attend today?"}]
      }
    },
    "id": "test-maya"
  }' | jq .result.task.artifacts[0].parts[0].text

# If the response shows "state": "TASK_STATE_WORKING", poll with GetTask:
TASK_ID="<task-id-from-response>"
curl -s -X POST http://localhost:8080/ \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d "{
    \"jsonrpc\": \"2.0\",
    \"method\": \"GetTask\",
    \"params\": { \"id\": \"$TASK_ID\", \"historyLength\": 10 },
    \"id\": \"get-task-1\"
  }" | jq .
```

## Checkpoint

- [ ] Quarkus running on port 8080
- [ ] AgentCard served at `/.well-known/agent-card.json` with three skills
- [ ] `SendMessage` endpoint answers session questions
- [ ] LLM uses tool calling to search real conference data

## Key Files

| File | Purpose |
|---|---|
| `ScheduleTool.java` | `@Tool` methods for searching sessions, filtering by time, speaker info |
| `ScheduleService.java` | `@RegisterAiService` — Quarkus auto-wires the LLM with the schedule tools |
| `ScheduleAgentCardProducer.java` | CDI `@Produces @PublicAgentCard` for the A2A AgentCard |
| `ScheduleAgentExecutorProducer.java` | CDI `@Produces` for the AgentExecutor (message handling) |
| `application.properties` | HTTP port, OpenAI model config, session data path, agent identity |

## Skills

| Skill ID | Description |
|---|---|
| `session-search` | Search sessions by topic, technology, speaker, or track |
| `session-recommend` | Personalized recommendations based on interests and availability |
| `speaker-info` | Speaker information and their sessions |
