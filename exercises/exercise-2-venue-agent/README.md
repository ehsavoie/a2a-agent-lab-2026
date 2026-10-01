# Exercise 2 — Venue & On-Site Operations Agent (20 min)

> *"Maya arrives at the venue. The 10:15 talk on vector indexing is in Hall B — but is there still room? She asks: 'Is Hall B full? Can I get a fast-track entry pass?'"*

Build a **Spring Boot + LangChain4j** agent that manages real-time venue information: IoT room capacity sensors, indoor navigation, and catering queue tracking. This exercise shows how the same A2A protocol works across different Java frameworks.

## Context

| | Quarkus (Exercise 1) | Spring Boot (Exercise 2) |
|---|---|---|
| **Runtime** | Quarkus | Spring Boot |
| **A2A library** | A2A Java SDK (`a2a-java-sdk-reference-jsonrpc`) | [spring-a2a](https://github.com/Sh1bari/spring-a2a) |
| **Transport** | JSON-RPC | REST |
| **DI style** | `@Produces` / `@RegisterAiService` | `@Bean` / `AiServices.builder()` |
| **Port** | 8080 | 8081 |

## What You Will Build

Two files need to be implemented:

1. **`VenueAgentConfig.java`** — Spring `@Configuration` class containing the `agentCard()` and `agentExecutor()` `@Bean` methods
2. **`VenueTool.java`** — `@Component` with LangChain4j `@Tool` methods for IoT sensor data *(already provided, study it)*

## Step 1 — Spring Boot Wiring

Open `VenueAgentConfig.java`. The `chatModel()` and `venueService()` beans are already provided. Implement the two remaining `@Bean` methods.

### `agentCard()` bean

Use `AgentCard.builder()` with:

1. `.name()`, `.description()`, `.version()` from the `@Value` injected fields
2. `.supportedInterfaces()` — a single `AgentInterface` with **`TransportProtocol.HTTP_JSON.asString()`** and `agentUrl` *(REST transport, unlike Exercise 1 which used JSON-RPC)*
3. `.capabilities(AgentCapabilities.builder().streaming(false).pushNotifications(false))`
4. `.skills()` — four skills: `room-capacity`, `indoor-map`, `catering-queue`, `entry-pass`
5. `.defaultInputModes(List.of("text"))` and `.defaultOutputModes(List.of("text"))`

> **Spring vs Quarkus:** In Spring Boot, `@Bean` methods in a `@Configuration` class replace Quarkus `@Produces` methods. The `AgentCard` bean is discovered automatically by spring-a2a — no `@PublicAgentCard` qualifier needed.

### `agentExecutor(VenueService venueService)` bean

Return an anonymous `AgentExecutor` with:

**`execute()`:**
1. Extract user text from the message parts
2. Call `emitter.startWork()`
3. Call `venueService.chat(userText)`
4. On success: `emitter.addArtifact(Collections.singletonList(new TextPart(response)), null, "response", null)` then `emitter.complete()`
5. On exception: `emitter.addArtifact(...)` with the error message then `emitter.fail()`

**`cancel()`:** throw `new TaskNotCancelableError()`

## Step 2 — VenueTool (IoT sensor data)

Open `VenueTool.java` (already implemented). The tool uses LangChain4j `@Tool` annotations to expose venue operations with hardcoded IoT sensor readings:

```
"Main Hall A" → capacity 2000, occupancy 85%
"Hall B"      → capacity 500,  occupancy 60%
"Room 201"    → capacity 150,  occupancy 72%
"Room 301"    → capacity 200,  occupancy 45%
"Room 401"    → capacity 120,  occupancy 90%
"Room 102"    → capacity 180,  occupancy 55%
"Lab Room B"  → capacity 60,   occupancy 40%
```

| Tool | Purpose |
|---|---|
| `checkRoomCapacity(roomName)` | Real-time capacity from IoT sensors |
| `getIndoorDirections(from, to)` | Walking directions between venue locations |
| `getCateringStatus()` | Current queue lengths at catering stations |
| `reserveEntryPass(attendeeName, roomName)` | Reserve a fast-track entry pass |

## Quick Start

Requires JDK 21+, Maven 3.9+, and an OpenAI API key with API billing enabled.

```bash
export OPENAI_API_KEY=your-api-key-here
cd exercises/exercise-2-venue-agent
mvn spring-boot:run
```

## Verify

```bash
# Fetch the AgentCard
curl -s http://localhost:8081/.well-known/agent-card.json \
  -H "A2A-Version: 1.0" | jq '{name, skills: [.skills[].id]}'

# Check room capacity and reserve a fast-track pass (REST transport)
curl -s -X POST http://localhost:8081/message:send \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "message": {
      "messageId": "msg-1",
      "role": "ROLE_USER",
      "parts": [{"text": "Is Hall B full? Can I get a fast-track entry pass for Maya?"}]
    }
  }' | jq .

# Check capacity by session room codes (agent resolves TBA codes to physical rooms)
curl -s -X POST http://localhost:8081/message:send \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "message": {
      "messageId": "msg-2",
      "role": "ROLE_USER",
      "parts": [{"text": "Is there still space in room TBA 7 and TBA 2?"}]
    }
  }' | jq .

# Retrieve a task by ID (REST GetTask)
# Replace <taskId> with the taskId from a SendMessage response
curl -s http://localhost:8081/tasks/<taskId> \
  -H "A2A-Version: 1.0" | jq .
```

> **Key difference from Exercise 1:** The Venue Agent uses the REST transport (`/message:send`, `/tasks/<id>`) whereas the Schedule Advisor uses JSON-RPC (`POST /` with `"method": "SendMessage"`). The AgentCard advertises the transport protocol, and any compliant A2A client adapts automatically.

## Checkpoint

- [ ] Venue Agent running on port 8081 alongside the Schedule Advisor on 8080
- [ ] Both serving AgentCards with different skills via the same A2A protocol
- [ ] IoT room capacity data accessible via tool calling
- [ ] `reserveEntryPass` returning a confirmation for Maya

## Skills

| Skill ID | Description |
|---|---|
| `room-capacity` | Check real-time room capacity and occupancy from IoT sensors |
| `indoor-map` | Get walking directions between rooms and areas in the venue |
| `catering-queue` | Check current queue lengths and wait times at catering stations |
| `entry-pass` | Reserve a fast-track entry pass for priority seating at a session room |
