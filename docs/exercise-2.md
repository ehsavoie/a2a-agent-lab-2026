# Exercise 2: The Venue & On-Site Operations Agent — Spring Boot + LangChain4j

**Time:** 20 minutes

> _"Maya arrives at the convention center. Hall B's talk on 'Scaling Vector Indexing in Enterprise Meshes' is about to start — but after her delayed flight, she's not sure there's still room. She types: 'Is Hall B full? Can I get a fast-track entry pass?'"_

## Overview

In this exercise you will build the **Venue & On-Site Operations Agent** — a Spring Boot microservice using **LangChain4j** and **[spring-a2a](https://github.com/Sh1bari/spring-a2a)** for A2A protocol support. It manages real-time venue data via simulated IoT sensors, indoor navigation, and catering queue tracking.

This is your second A2A agent, and it runs on a completely different Java framework. The key learning: **the AgentCard contract is the interface, not the framework**. An A2A client that talks to your Quarkus agent from Exercise 1 will talk to this Spring Boot agent without any code changes.

### What you will learn

| Concept | What it is |
|---------|-----------|
| **LangChain4j + spring-a2a** | How to produce AgentCard and AgentExecutor as Spring `@Bean` components |
| **AiServices.builder()** | Using LangChain4j's `AiServices` pattern with Spring Boot DI |
| **LangChain4j @Tool** | Exposing simulated sensor readings through `@Tool` / `@P` annotations |
| **Cross-framework interop** | Two agents on different frameworks, same A2A protocol |

---

## Key Insight: Same Protocol, Different Framework

```
┌──────────────────────┐     ┌──────────────────────────┐
│ Schedule & Content   │     │ Venue & On-Site Ops      │
│ Advisor              │     │ Agent                    │
│ Quarkus (Java A2A SDK)       │     │ Spring Boot (spring-a2a) │
│ :8080                │     │ :8081                    │
└──────────┬───────────┘     └──────────┬───────────────┘
           │                            │
           └────────────┬───────────────┘
                        │
               ┌────────▼────────┐
               │  Same A2A       │
               │  Protocol       │
               │  Same SDK       │
               │  Different      │
               │  Skills         │
               └─────────────────┘
```

An A2A client only needs the agent's URL. It fetches the AgentCard, reads the skills, sends messages. The framework behind the URL is invisible.

---

## Spring Boot vs Quarkus (Java A2A SDK)

Before writing code, let's compare the two frameworks side by side:

| Aspect | Quarkus / Java A2A SDK (Exercise 1) | Spring Boot (Exercise 2) |
|--------|-------------------------------|--------------------------|
| Bean registration | `@ApplicationScoped` | `@Component` |
| Producer method | `@Produces` | `@Bean` in `@Configuration` |
| AI framework | LangChain4j (`@RegisterAiService`) | LangChain4j (`AiServices.builder()`) |
| A2A integration | A2A Java SDK (Java A2A SDK) | [spring-a2a](https://github.com/Sh1bari/spring-a2a) |
| Tool annotation | `@Tool("desc")` | `@Tool("desc")` |
| AgentCard path | `/.well-known/agent-card.json` | `/.well-known/agent-card.json` |
| Config injection | `@ConfigProperty` (MicroProfile) | `@Value` (Spring) |
| Startup | `mvn quarkus:dev` | `mvn spring-boot:run` |

The A2A protocol (AgentCard contract, SendMessage/GetTask methods) is identical. Both use the same `org.a2aproject.sdk` types. Only the DI wiring and AI Service creation differ.

---

## Step 1: Explore the Project

Open the exercise project:

```bash
cd exercises/exercise-2-venue-agent
```

Check `pom.xml` — key dependencies:
- `spring-boot-starter-web` — embedded HTTP server
- `langchain4j-google-ai-gemini-spring-boot-starter` — LangChain4j with Google AI Gemini
- `a2a-spring-boot-starter-server-rest` — A2A REST transport for Spring Boot

---

## Step 2: Build the VenueTool

The `VenueTool` provides IoT-style venue data to the LLM. Instead of reading from a JSON file (like the Session Agent), this agent has hardcoded venue data that simulates real-time sensor readings.

Open `src/main/java/dev/devconf/venue/VenueTool.java`:

```java
@Component
public class VenueTool {

    private Map<String, RoomInfo> rooms;
    private Map<String, CateringStation> cateringStations;

    public record RoomInfo(String name, int capacity, int occupancyPercent) {
        public int available() {
            return capacity - (capacity * occupancyPercent / 100);
        }
    }
```

Four tools are exposed to the LLM using LangChain4j's `@Tool` and `@P` annotations:

- **`checkRoomCapacity(String roomName)`** — Returns real-time occupancy from IoT sensors (capacity, current %, available seats, status)
- **`getIndoorDirections(String from, String to)`** — Walking directions between rooms and landmarks
- **`getCateringStatus()`** — Queue lengths and wait times at all catering stations
- **`reserveEntryPass(String attendeeName, String roomName)`** — Reserves a fast-track entry pass with a unique pass ID

> **Key insight:** In a production system, these tool methods would call actual IoT APIs and venue management systems. For the lab, the hardcoded data simulates what those APIs would return.

---

## Step 3: Wire with Spring Configuration

Open `src/main/java/dev/devconf/venue/VenueAgentConfig.java`:

```java
@Configuration
public class VenueAgentConfig {

    private static final String SYSTEM_INSTRUCTION = """
            You are the DevConf 2026 Venue & On-Site Operations Agent.
            You manage real-time venue information including room capacity
            via IoT sensors, indoor navigation, and catering queue tracking.
            """;

    @Bean
    public VenueService venueService(ChatModel model, VenueTool venueTool) {
        return AiServices.builder(VenueService.class)
                .chatModel(model)
                .tools(venueTool)
                .build();
    }

    @Bean
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name(agentName)
                .version(agentVersion)
                .supportedInterfaces(List.of(
                    new AgentInterface(
                        TransportProtocol.HTTP_JSON.asString(), agentUrl)))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .skills(List.of(
                    AgentSkill.builder()
                        .id("room-capacity")
                        .name("Room Capacity Check")
                        .description("Check real-time room capacity from IoT sensors.")
                        .build(),
                    // ... indoor-map, catering-queue, entry-pass ...
                ))
                .build();
    }

    @Bean
    public AgentExecutor agentExecutor(VenueService venueService) {
        return new AgentExecutor() {
            @Override
            public void execute(RequestContext context, AgentEmitter emitter)
                    throws A2AError {
                String userText = extractText(context.getMessage());
                emitter.startWork();
                String response = venueService.chat(userText);
                emitter.addArtifact(
                    Collections.singletonList(new TextPart(response)),
                    null, "response", null);
                emitter.complete();
            }
            // ...
        };
    }
}
```

Compare this with Exercise 1:
- `@Bean` replaces `@Produces`
- `@Value` replaces `@ConfigProperty`
- `AiServices.builder(VenueService.class)` replaces `@RegisterAiService`
- Same `AgentCard.builder()`, `AgentExecutor`, `AgentEmitter` pattern — identical SDK types

---

## Step 4: Configure and Run

Check `src/main/resources/application.properties`:

```properties
server.port=8081

langchain4j.google-ai-gemini.chat-model.api-key=${GOOGLE_AI_GEMINI_API_KEY}
langchain4j.google-ai-gemini.chat-model.model-name=gemini-2.5-flash
langchain4j.google-ai-gemini.chat-model.temperature=0.7

a2a.agent.name=Venue & On-Site Operations Agent
a2a.agent.description=Manages real-time venue operations including IoT room capacity, indoor mapping, and catering tracking
a2a.agent.version=1.0.0
a2a.agent.url=http://localhost:8081
```

Make sure your `GOOGLE_AI_GEMINI_API_KEY` environment variable is set, then start:

```bash
mvn spring-boot:run
```

---

## Step 5: Test Your Agent

### 5a. Fetch the AgentCard

```bash
curl -s http://localhost:8081/.well-known/agent-card.json | jq .
```

You should see the agent's card with skills: `room-capacity`, `indoor-map`, `catering-queue`, `entry-pass`.

### 5b. Check Room Capacity

```bash
curl -s -X POST http://localhost:8081/message:send \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{
    "message": {
      "messageId": "msg-1",
      "role": "ROLE_USER",
      "parts": [{"text": "Is Hall B full? How many seats are left?"}]
    }
  }' | jq .
```

The agent should report Hall B at 60% capacity with ~200 available seats.

### 5c. Reserve a Fast-Track Pass

```bash
curl -s -X POST http://localhost:8081/message:send \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{
    "message": {
      "messageId": "msg-2",
      "role": "ROLE_USER",
      "parts": [{"text": "Reserve a fast-track entry pass for Maya at Hall B"}]
    }
  }' | jq .
```

You should get a confirmation with a unique pass ID.

### 5d. Retrieve a Task by ID

The `SendMessage` response includes a `taskId`. You can retrieve the full task state at any time using the REST `GetTask` endpoint:

```bash
# Replace <taskId> with the taskId from the SendMessage response
curl -s http://localhost:8081/tasks/<taskId> \
  -H 'A2A-Version: 1.0' | jq .
```

This returns the complete task with its status, messages, and artifacts — useful for polling long-running tasks or retrieving results later.

---

## Step 6: Cross-Runtime Verification

With both agents running (Exercise 1 on port 8080, Exercise 2 on port 8081), verify they both speak the same protocol:

### Compare AgentCards

```bash
# Exercise 1 (Quarkus)
curl -s http://localhost:8080/.well-known/agent-card.json \
  | jq '{name, skills: [.skills[].id]}'

# Exercise 2 (Spring Boot)
curl -s http://localhost:8081/.well-known/agent-card.json \
  | jq '{name, skills: [.skills[].id]}'
```

You should see **different skills** from each agent — but the **same A2A protocol structure**.

### Send Messages to Both

```bash
# Ask the Schedule Advisor about sessions (JSON-RPC)
curl -s -X POST http://localhost:8080/ \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"jsonrpc":"2.0","method":"SendMessage","params":{"message":{"messageId":"msg-1","role":"ROLE_USER","parts":[{"text":"What agentic AI sessions are available on October 7?"}]}},"id":"1"}' \
  | jq .result.status.state

# Ask the Venue Agent about room capacity (REST)
curl -s -X POST http://localhost:8081/message:send \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{"message":{"messageId":"msg-1","role":"ROLE_USER","parts":[{"text":"Is Hall B full?"}]}}' \
  | jq .result.status.state
```

Both should return `"completed"` — different skills, different frameworks, same protocol.

---

## Checkpoint

At this point you should have:

- [x] A Spring Boot agent running on port 8081
- [x] Four venue-focused skills: room-capacity, indoor-map, catering-queue, entry-pass
- [x] IoT-style tool data with real-time capacity readings
- [x] Two agents on two different frameworks, both speaking A2A
- [x] The realization that **the AgentCard is the contract** — the framework is invisible to clients

**You now have two agents in the mesh.** The Schedule Advisor knows about sessions. The Venue Agent knows about rooms, directions, and catering. Neither agent knows the other exists — but in Exercise 4, the Orchestrator will discover both and route queries to the right one.

---

> **Maya's story continues:** The Venue Agent confirmed Hall B is at 60% capacity and reserved a fast-track entry pass (FTP-A3B7C9D1) for Maya. She can walk right in. But first — how does she get from the airport to the venue? That's the Travel Agent's job, in Exercise 3.

---

> **Troubleshooting:**
>
> - **"API key not valid"**: Make sure your `GOOGLE_AI_GEMINI_API_KEY` environment variable is set with a valid Google AI Studio API key.
> - **Port conflict**: Make sure no other service is running on port 8081.
> - **LangChain4j auto-config issues**: Ensure `langchain4j-google-ai-gemini-spring-boot-starter` is in your classpath — it auto-configures the `ChatModel` bean that `AiServices.builder()` uses.
