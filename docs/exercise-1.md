# Exercise 1: Your First A2A Agent — The Schedule & Content Advisor

**Time:** 30 minutes

> _"It is 8:15 AM on October 7 — Day 1 of Devoxx Belgium 2026. Maya just landed at Brussels airport, but her flight was delayed. She opens the DevSphere app and types: 'I'm interested in agentic AI and Java agents — what talks should I catch today?' The Schedule & Content Advisor springs into action."_

## Overview

In this exercise you will build the first agent in the DevSphere mesh — the **Schedule & Content Advisor** — using **Jakarta EE** and the **A2A Jakarta EE SDK (Java A2A SDK)**. Deployed as a standard WAR on **WildFly 40**, it deep-scans the summit's session catalog, speaker bios, and domain tracks to match attendee interests to specific talks.

By the end, your agent will:

- Advertise its capabilities via a standard **AgentCard**
- Accept questions from any A2A client via `SendMessage`
- Use an LLM (Google Gemini) with **tool calling** to search the real conference schedule
- Filter sessions by time — so when Maya arrives at 9:45 AM, she only sees sessions she can still attend
- Return structured results following the A2A **Task** lifecycle

### What you will learn

| Concept | What it is |
|---------|-----------|
| **AgentCard** | JSON metadata at `/.well-known/agent-card.json` that advertises your agent's identity, skills, and capabilities |
| **AgentExecutor** | The handler that receives A2A messages and produces responses |
| **Task lifecycle** | How requests flow: `SUBMITTED` → `WORKING` → `COMPLETED` (or `FAILED`) |
| **Message & Part model** | Messages carry a `role` (USER or AGENT) and an array of typed `Part` objects |
| **Java A2A SDK** | The A2A Jakarta EE SDK — `a2a-jakarta-jsonrpc` — that provides the JSON-RPC transport on WildFly |

---

## A2A Concepts Primer

Before we write code, let's understand the four building blocks of the A2A protocol.

### AgentCard

Every A2A agent publishes a JSON document at `/.well-known/agent-card.json`. Think of it as a machine-readable "business card" — it tells other agents and clients:

- **Who am I?** — name, description, version
- **What can I do?** — a list of `skills`, each with an id, description, tags, and examples
- **How to talk to me** — supported transport (JSON-RPC), endpoint URL
- **What I support** — capabilities like streaming, push notifications

### Task Lifecycle

When a client sends a message, the server creates a **Task** that transitions through states:

```
SUBMITTED → WORKING → COMPLETED
                   ↘ FAILED
                   ↘ CANCELED
```

The `WORKING` state means the agent is processing. When done, it moves to `COMPLETED` and attaches one or more **Artifacts** (the response content).

### Message & Part Model

A **Message** has:
- `role` — either `user` or `agent`
- `parts` — an array of typed content: `TextPart` (plain text), `FilePart` (binary data), or `DataPart` (structured JSON)

For this exercise we will only use `TextPart`.

### JSON-RPC Methods

A2A uses JSON-RPC 2.0. The two methods we will use:
- `SendMessage` — send a message and get a response
- `GetTask` — retrieve a task by ID

---

## Step 1: Explore the Project

The exercise project is already set up at `exercises/exercise-1-schedule-advisor/`. Open it and examine the `pom.xml`:

```bash
cd exercises/exercise-1-schedule-advisor
```

Key dependencies:
- `a2a-jakarta-jsonrpc` — A2A Jakarta EE transport (JSON-RPC on JAX-RS)
- `a2a-java-sdk-server-common` — A2A server-side SDK
- `langchain4j` + `langchain4j-google-ai-gemini` — LangChain4j with Google AI Gemini support
- Jakarta EE APIs (CDI, JAX-RS) — provided by WildFly at runtime

### WAR Packaging

Unlike a Quarkus or Spring Boot agent, this agent is packaged as a **standard Jakarta EE WAR** and deployed on **WildFly 40**. The `wildfly-maven-plugin` provisions a complete WildFly server in `target/wildfly/` during `mvn package`:

```xml
<packaging>war</packaging>

<plugin>
    <groupId>org.wildfly.plugins</groupId>
    <artifactId>wildfly-maven-plugin</artifactId>
    <configuration>
        <feature-packs>
            <feature-pack>
                <groupId>org.wildfly</groupId>
                <artifactId>wildfly-galleon-pack</artifactId>
                <version>40.0.0.Final</version>
            </feature-pack>
        </feature-packs>
        <name>ROOT.war</name>
    </configuration>
</plugin>
```

The `ROOT.war` deployment name means the agent is accessible at the root context path — no `/app` prefix.

---

## Step 2: Explore the Conference Data

Open `conference-data/sessions.json` — this is the data our agent will search. Each session has:

```json
{
  "id": "DVX-10020",
  "title": "From AI to Agent: A Field Guide to Agentic Patterns in LangChain4j",
  "speaker": "Loïc Magnette",
  "track": "Agentic Engineering & Tooling",
  "room": "TBA 4",
  "date": "2026-10-05",
  "time": "09:30",
  "duration": 180,
  "level": "Intermediate",
  "description": "This talk shows how to evolve a Java AI app from simple LLM calls into controllable agentic systems...",
  "tags": ["observability", "agentic", "supervisor", "workflow"]
}
```

There are 203 real Devoxx Belgium 2026 sessions across 8 tracks (Agentic Engineering & Tooling, Architecture & Modernization, Java Language & Platform, Security, etc.) and 5 days (Oct 5-6 deep dives, Oct 7-9 conference).

---

## Step 3: Build the ScheduleTool

The `ScheduleTool` gives the LLM access to the real conference data. LangChain4j's `@Tool` annotation exposes methods as callable tools that the model can invoke.

Create `src/main/java/dev/devconf/schedule/ScheduleTool.java`:

```java
public class ScheduleTool {

    private List<Session> sessions;

    public ScheduleTool(String sessionDataPath) {
        // Load sessions from JSON file using Jackson
        ObjectMapper mapper = new ObjectMapper();
        Path path = Path.of(sessionDataPath);
        sessions = mapper.readValue(path.toFile(), new TypeReference<>() {});
    }
```

Five tools are exposed to the LLM:

- **`searchSessions(String query)`** — Searches sessions by keyword match against title, track, speaker, description, and tags
- **`listTracks()`** — Returns all distinct track names
- **`getScheduleByDate(String date)`** — Returns the full schedule for a given day
- **`filterSessionsAfterTime(String date, String time)`** — Returns sessions starting at or after a given time (for attendees arriving late, like Maya)
- **`getSpeakerInfo(String speakerName)`** — Returns speaker info from sessions data

> **Key insight:** The `@Tool` annotation includes a natural-language description. The LLM reads this to decide when and how to call the tool. Write clear descriptions!

The `filterSessionsAfterTime` tool is particularly important for Maya's scenario — when she tells the agent she arrives at 9:45 AM, the LLM will call this tool to filter out sessions she has already missed.

The `Session` record at the bottom maps directly to the JSON structure:

```java
public record Session(
    String id, String title, String speaker, String track,
    String room, String date, String time, int duration,
    String description, List<String> tags
) {}
```

---

## Step 4: Build the ScheduleService

The `ScheduleService` is a LangChain4j **AI Service** — an interface that LangChain4j implements at runtime by wiring together the LLM, tools, and system prompt.

Create `src/main/java/dev/devconf/schedule/ScheduleService.java`:

```java
public interface ScheduleService {

    @SystemMessage("""
            You are the DevConf 2026 Schedule & Content Advisor — a knowledgeable
            assistant that helps conference attendees find the perfect sessions.
            You deep-scan the summit's session catalog, speaker bios, and domain tracks.
            Match attendee skill levels and interests to specific talks.

            You have access to the full conference schedule through your tools.
            Always use your search tools rather than making up session information.
            If the attendee mentions arriving late or a specific arrival time,
            use filterSessionsAfterTime to exclude earlier sessions.
            """)
    String chat(@UserMessage String userMessage);
}
```

Since we are on plain Jakarta EE (not Quarkus), we cannot use `@RegisterAiService`. Instead, we wire the AI Service programmatically in a CDI producer.

---

## Step 5: Wire the AI Service with CDI

Create `src/main/java/dev/devconf/schedule/ScheduleServiceProducer.java`:

```java
@ApplicationScoped
public class ScheduleServiceProducer {

    @ConfigProperty(name = "gemini.api-key")
    String geminiApiKey;

    @ConfigProperty(name = "gemini.model-name", defaultValue = "gemini-2.5-flash")
    String modelName;

    @ConfigProperty(name = "session.data.path",
                    defaultValue = "../../conference-data/sessions.json")
    String sessionDataPath;

    private ScheduleService scheduleService;

    @PostConstruct
    void init() {
        GoogleAiGeminiChatModel chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName(modelName)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(60))
                .build();

        ScheduleTool scheduleTool = new ScheduleTool(sessionDataPath);

        scheduleService = AiServices.builder(ScheduleService.class)
                .chatModel(chatModel)
                .tools(scheduleTool)
                .build();
    }

    @Produces
    public ScheduleService getScheduleService() {
        return scheduleService;
    }
}
```

Key points:
- `@ConfigProperty` injects values from `META-INF/microprofile-config.properties` (MicroProfile Config, provided by WildFly)
- The `ScheduleTool` is created manually (not as a CDI bean) and passed to `AiServices.builder()`
- The `ScheduleService` proxy is produced as a CDI bean for injection elsewhere

---

## Step 6: Implement the AgentCard Producer

The AgentCard is how your agent introduces itself to the A2A world. We produce it using CDI.

Create `src/main/java/dev/devconf/schedule/ScheduleAgentCardProducer.java`:

```java
@ApplicationScoped
public class ScheduleAgentCardProducer {

    @ConfigProperty(name = "a2a.agent.name")
    String agentName;

    // ... other config properties ...

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name(agentName)
                .description(agentDescription)
                .version(agentVersion)
                .supportedInterfaces(List.of(
                    new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), agentUrl)
                ))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .skills(List.of(
                    AgentSkill.builder()
                        .id("session-search")
                        .name("Session Search")
                        .description("Search for conference sessions by topic, technology, speaker, or track.")
                        .tags(List.of("sessions", "search", "schedule"))
                        .examples(List.of("What sessions about agentic AI are on October 7?"))
                        .build(),
                    AgentSkill.builder()
                        .id("session-recommend")
                        .name("Session Recommendations")
                        .description("Get personalized session recommendations based on interests and availability.")
                        .tags(List.of("recommendations", "personalization"))
                        .examples(List.of("I arrive at 9:45 AM on October 7 and like agentic AI. What should I attend?"))
                        .build(),
                    AgentSkill.builder()
                        .id("speaker-info")
                        .name("Speaker Information")
                        .description("Get information about conference speakers and their sessions.")
                        .tags(List.of("speakers", "bios"))
                        .examples(List.of("Who is speaking about Quarkus?"))
                        .build()
                ))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
    }
}
```

Key points:
- `@Produces` makes this a CDI producer method — the Java A2A SDK picks it up automatically
- `@PublicAgentCard` is an SDK qualifier that tells the A2A server to serve this card at `/.well-known/agent-card.json`
- **Skills** are the most important part — they tell the Orchestrator and other agents what you can do

> **Note:** Config values come from `META-INF/microprofile-config.properties`, making the agent easy to reconfigure for different environments without changing code.

---

## Step 7: Implement the AgentExecutor Producer

The `AgentExecutor` is where the real work happens. It receives incoming A2A messages and produces responses.

Create `src/main/java/dev/devconf/schedule/ScheduleAgentExecutorProducer.java`:

```java
@ApplicationScoped
public class ScheduleAgentExecutorProducer {

    @Inject
    ScheduleService scheduleService;

    @Produces
    public AgentExecutor agentExecutor() {
        return new AgentExecutor() {

            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                // 1. Extract user's text from the incoming message
                String userText = extractText(context.getMessage());

                // 2. Signal that work has started (Task → WORKING)
                emitter.startWork(emitter.messageBuilder()
                    .parts(List.of(new TextPart("Scanning the session catalog...")))
                    .build());

                try {
                    // 3. Call the LLM via the AI Service
                    String response = scheduleService.chat(userText);

                    // 4. Attach the response as an Artifact and complete
                    emitter.addArtifact(List.of(new TextPart(response)));
                    emitter.complete();   // Task → COMPLETED
                } catch (Exception e) {
                    // 5. On failure, report the error
                    emitter.updateStatus(TaskState.TASK_STATE_FAILED,
                        emitter.messageBuilder()
                            .parts(List.of(new TextPart(
                                "Error: " + e.getMessage())))
                            .build());
                }
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
                emitter.cancel();
            }
        };
    }

    private String extractText(Message message) {
        if (message == null || message.parts() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                sb.append(textPart.text());
            }
        }
        return sb.toString().trim();
    }
}
```

The flow maps directly to the Task lifecycle:

```
Client sends SendMessage
  → execute() is called
  → emitter.startWork()          → Task state: WORKING
  → scheduleService.chat()       → LLM + tool calls happen
  → emitter.addArtifact()        → Attach response content
  → emitter.complete()           → Task state: COMPLETED
  → Response returned to client
```

---

## Step 8: Add the JAX-RS Application

A minimal JAX-RS application class activates the REST endpoint:

Create `src/main/java/dev/devconf/schedule/ScheduleApplication.java`:

```java
@ApplicationPath("/")
public class ScheduleApplication extends Application {
}
```

---

## Step 9: Configure and Run

Check `src/main/resources/META-INF/microprofile-config.properties`:

```properties
# A2A authorization
a2a.authorization.required=false

# Session data path
session.data.path=../../conference-data/sessions.json

# Google AI Gemini LLM Configuration
gemini.api-key=${GOOGLE_AI_GEMINI_API_KEY}
gemini.model-name=gemini-2.5-flash

# A2A Agent Configuration
a2a.agent.name=Schedule & Content Advisor
a2a.agent.description=Deep-scans the DevConf session catalog and matches attendee interests to talks
a2a.agent.version=1.0.0
a2a.agent.url=http://localhost:8080
```

Make sure your `GOOGLE_AI_GEMINI_API_KEY` environment variable is set, then build and start:

```bash
# Build the WAR and provision WildFly
mvn package -Dsession.data.path=$(pwd)/../../conference-data/sessions.json

# Start WildFly (HTTP on port 8080)
./target/wildfly/bin/standalone.sh \
  -Dsession.data.path=$(pwd)/../../conference-data/sessions.json
```

You should see WildFly start and the A2A agent card become available.

> **Note:** The `wildfly-maven-plugin` provisions a complete WildFly server in `target/wildfly/` with just the layers needed to run your WAR. The `ROOT.war` deployment name means the agent is accessible at the root context path.

---

## Step 10: Test Your Agent

### 10a. Fetch the AgentCard

```bash
curl -s http://localhost:8080/.well-known/agent-card.json | jq .
```

You should see your agent's card with its name, skills, and capabilities:

```json
{
  "name": "Schedule & Content Advisor",
  "description": "Deep-scans the DevConf session catalog...",
  "version": "1.0.0",
  "skills": [
    {
      "id": "session-search",
      "name": "Session Search",
      "description": "Search for conference sessions..."
    },
    {
      "id": "session-recommend",
      "name": "Session Recommendations",
      "description": "Get personalized session recommendations..."
    },
    {
      "id": "speaker-info",
      "name": "Speaker Information",
      "description": "Get information about conference speakers..."
    }
  ],
  "capabilities": {
    "streaming": false,
    "pushNotifications": false
  }
}
```

### 10b. Send a Message

```bash
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
```

The response will be a JSON-RPC result containing a **Task** with status `COMPLETED` and an **Artifact** with the LLM's answer about agentic AI sessions on the first conference day.

### 10c. Maya's Scenario — Late Arrival

This is the key test. Maya arrives at Devoxx Belgium at 9:45 AM on October 7 (the first conference day). Ask the agent for recommendations that skip the sessions she missed:

```bash
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
    "id": "test-maya"
  }' | jq .result.task.artifacts[0].parts[0].text
```

The LLM should use `filterSessionsAfterTime("2026-10-07", "09:45")` to exclude the 09:30 Welcome keynote and recommend sessions like "A Fleet of AI Agents, Each in Its Own Sandbox" (10:30 by Jean-Laurent de Morlhon), "Agentic Engineering Reconversion" (11:10 by Victor Rentea), and "A Year of Agentic AI Evolution" (14:00 by Mario Fusco).

### 10d. Speaker Lookup

```bash
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
        "parts": [{"text": "What sessions is Mario Fusco speaking at?"}]
      }
    },
    "id": "test-speaker"
  }' | jq .result.task.artifacts[0].parts[0].text
```

### 10e. Get Task Result

When you send a message, the response includes a Task. If the task's status is `TASK_STATE_WORKING`, the agent is still processing. Copy the `task.id` from the response and use `GetTask` to poll until it completes:

```bash
# Replace with the task ID from the SendMessage response
TASK_ID="af4e2602-dd27-4702-aa7f-752198cef538"

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

Once the task reaches `TASK_STATE_COMPLETED`, the response will contain the full Task object with its artifacts (the agent's answer) and conversation history.

---

## Checkpoint

At this point you should have:

- [x] A running WildFly application on port 8080
- [x] An AgentCard served at `/.well-known/agent-card.json` with three skills
- [x] A working `SendMessage` endpoint that answers session questions
- [x] The LLM using tool calling to search real conference data (not hallucinating)
- [x] Time-aware session filtering for late arrivals (Maya's scenario)

**You've built your first A2A agent — the Schedule & Content Advisor.** It speaks a standard protocol that any A2A client — in any language, on any framework — can talk to. In the next exercise, you will add the Venue & On-Site Operations Agent on Spring Boot to manage real-time room capacity and indoor mapping.

---

> **Troubleshooting:**
>
> - **"API key not valid"**: Make sure your `GOOGLE_AI_GEMINI_API_KEY` environment variable is set with a valid Google AI Studio API key.
> - **Empty responses**: The first LLM call can be slow. Increase the timeout in `microprofile-config.properties` if needed.
> - **"No sessions found"**: Verify the `session.data.path` system property points correctly to `conference-data/sessions.json`. Pass it via `-Dsession.data.path=...` when starting WildFly.
> - **Port conflict on 8080**: If another service is using port 8080, start WildFly with a port offset: `./target/wildfly/bin/standalone.sh -Djboss.socket.binding.port-offset=10` and update `a2a.agent.url` accordingly.
