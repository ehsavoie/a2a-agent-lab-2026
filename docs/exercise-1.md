# Exercise 1: Your First A2A Agent — The Session Recommender

**Time:** 30 minutes

> _"The conference organizers need an agent that can answer attendee questions about sessions: 'What talks are about AI?', 'When is the Kubernetes keynote?', 'Recommend sessions for a Java developer interested in microservices.'"_

## Overview

In this exercise you will build a fully functional A2A agent from scratch using **Quarkus** and **LangChain4j**. By the end, your agent will:

- Advertise its capabilities via a standard **AgentCard**
- Accept questions from any A2A client via `message/send`
- Use an LLM (Ollama/Mistral) with **tool calling** to search the real conference schedule
- Return structured results following the A2A **Task** lifecycle

### What you will learn

| Concept | What it is |
|---------|-----------|
| **AgentCard** | JSON metadata at `/.well-known/agent.json` that advertises your agent's identity, skills, and capabilities |
| **AgentExecutor** | The handler that receives A2A messages and produces responses |
| **Task lifecycle** | How requests flow: `SUBMITTED` → `WORKING` → `COMPLETED` (or `FAILED`) |
| **Message & Part model** | Messages carry a `role` (USER or AGENT) and an array of typed `Part` objects |

---

## A2A Concepts Primer

Before we write code, let's understand the four building blocks of the A2A protocol.

### AgentCard

Every A2A agent publishes a JSON document at `/.well-known/agent.json`. Think of it as a machine-readable "business card" — it tells other agents and clients:

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
- `message/send` — send a message and get a response
- `tasks/get` — retrieve a task by ID

---

## Step 1: Explore the Project

The exercise project is already set up at `exercises/exercise-1-session-agent/`. Open it and examine the `pom.xml`:

```bash
cd exercises/exercise-1-session-agent
```

Key dependencies:
- `quarkus-langchain4j-ollama` — LangChain4j integration with Ollama
- `a2a-java-sdk-reference-jsonrpc` — A2A server-side SDK (JSON-RPC transport)
- `a2a-java-sdk-client` — A2A client SDK (we will use this later)

> **Note:** The A2A Java SDK may need to be built from source or installed from a project-specific Maven repository. See the main README for setup instructions.

---

## Step 2: Explore the Conference Data

Open `conference-data/sessions.json` — this is the data our agent will search. Each session has:

```json
{
  "id": "S002",
  "title": "Building Production-Grade AI Agents with LangChain4j",
  "speaker": "Dmytro Liubarskyi",
  "track": "AI & ML",
  "room": "Room 201",
  "date": "2026-10-15",
  "time": "11:00",
  "duration": 45,
  "description": "Learn how to build reliable AI agents...",
  "tags": ["langchain4j", "ai", "agents", "java", "production"]
}
```

There are 12 sessions across multiple tracks (Keynote, AI & ML, Cloud Native, Frameworks, Security, etc.) and two conference days.

---

## Step 3: Build the SessionTool

The `SessionTool` gives the LLM access to the real conference data. LangChain4j's `@Tool` annotation exposes methods as callable tools that the model can invoke.

Create `src/main/java/dev/devconf/session/SessionTool.java`:

```java
@ApplicationScoped
public class SessionTool {

    @ConfigProperty(name = "session.data.path",
                    defaultValue = "../../conference-data/sessions.json")
    String sessionDataPath;

    private List<Session> sessions;

    @PostConstruct
    void loadSessions() {
        // Load sessions from JSON file using Jackson
        ObjectMapper mapper = new ObjectMapper();
        Path path = Path.of(sessionDataPath);
        sessions = mapper.readValue(path.toFile(), new TypeReference<>() {});
    }
```

Three tools are exposed to the LLM:

- **`searchSessions(String query)`** — Searches sessions by keyword match against title, track, speaker, description, and tags
- **`listTracks()`** — Returns all distinct track names
- **`getScheduleByDate(String date)`** — Returns the full schedule for a given day

Each method returns a formatted string that the LLM will use to compose its answer.

> **Key insight:** The `@Tool` annotation includes a natural-language description. The LLM reads this to decide when and how to call the tool. Write clear descriptions!

The `Session` record at the bottom maps directly to the JSON structure:

```java
public record Session(
    String id, String title, String speaker, String track,
    String room, String date, String time, int duration,
    String description, List<String> tags
) {}
```

---

## Step 4: Build the SessionService

The `SessionService` is a LangChain4j **AI Service** — an interface that LangChain4j implements at runtime by wiring together the LLM, tools, and system prompt.

Create `src/main/java/dev/devconf/session/SessionService.java`:

```java
@RegisterAiService(tools = SessionTool.class)
public interface SessionService {

    @SystemMessage("""
            You are the DevConf 2026 Session Recommender — a friendly,
            knowledgeable assistant that helps conference attendees find
            the perfect sessions to attend.

            You have access to the full conference schedule through your tools.
            Always use your search tools rather than making up session information.
            """)
    String chat(@UserMessage String userMessage);
}
```

That's it — Quarkus and LangChain4j handle the rest:
- `@RegisterAiService` tells Quarkus to generate a CDI bean implementing this interface
- `tools = SessionTool.class` wires the tool we built in Step 3
- `@SystemMessage` sets the agent's persona and instructions
- `@UserMessage` marks the user input parameter

---

## Step 5: Implement the AgentCard Producer

The AgentCard is how your agent introduces itself to the A2A world. We produce it using CDI.

Create `src/main/java/dev/devconf/session/SessionAgentCardProducer.java`:

```java
@ApplicationScoped
public class SessionAgentCardProducer {

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
                        .description("Search for conference sessions...")
                        .tags(List.of("sessions", "search"))
                        .examples(List.of("What AI sessions are available?"))
                        .build(),
                    AgentSkill.builder()
                        .id("session-recommend")
                        .name("Session Recommendations")
                        .description("Get personalized recommendations...")
                        .tags(List.of("recommendations"))
                        .examples(List.of("I'm a Java dev, what should I attend?"))
                        .build()
                ))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
    }
}
```

Key points:
- `@Produces` makes this a CDI producer method — the SDK picks it up automatically
- `@PublicAgentCard` is an SDK qualifier that tells the A2A server to serve this card at `/.well-known/agent.json`
- **Skills** are the most important part — they tell other agents what you can do

> **Note:** Config values come from `application.properties`, making the agent easy to reconfigure for different environments without changing code.

---

## Step 6: Implement the AgentExecutor Producer

The `AgentExecutor` is where the real work happens. It receives incoming A2A messages and produces responses.

Create `src/main/java/dev/devconf/session/SessionAgentExecutorProducer.java`:

```java
@ApplicationScoped
public class SessionAgentExecutorProducer {

    @Inject
    SessionService sessionService;

    @Produces
    public AgentExecutor agentExecutor() {
        return new AgentExecutor() {

            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                // 1. Extract user's text from the incoming message
                String userText = extractText(context.getMessage());

                // 2. Signal that work has started (Task → WORKING)
                emitter.startWork(emitter.messageBuilder()
                    .parts(List.of(new TextPart("Looking up sessions...")))
                    .build());

                try {
                    // 3. Call the LLM via the AI Service
                    String response = sessionService.chat(userText);

                    // 4. Attach the response as an Artifact and complete
                    emitter.addArtifact(List.of(new TextPart(response)));
                    emitter.complete();   // Task → COMPLETED
                } catch (Exception e) {
                    // 5. On failure, report the error
                    emitter.updateStatus(TaskState.TASK_STATE_FAILED, ...);
                }
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
                emitter.cancel();
            }
        };
    }
}
```

The flow maps directly to the Task lifecycle:

```
Client sends message/send
  → execute() is called
  → emitter.startWork()          → Task state: WORKING
  → sessionService.chat()        → LLM + tool calls happen
  → emitter.addArtifact()        → Attach response content
  → emitter.complete()           → Task state: COMPLETED
  → Response returned to client
```

The `extractText` helper walks through the message's `Part` list and concatenates all `TextPart` values:

```java
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
```

---

## Step 7: Configure and Run

Open `src/main/resources/application.properties`:

```properties
# Quarkus HTTP
quarkus.http.port=8080

# Ollama LLM Configuration
quarkus.langchain4j.ollama.base-url=http://localhost:11434
quarkus.langchain4j.ollama.chat-model.model-id=mistral
quarkus.langchain4j.ollama.chat-model.temperature=0.7
quarkus.langchain4j.ollama.timeout=60s

# Session data path
session.data.path=../../conference-data/sessions.json

# A2A Agent Configuration
a2a.agent.name=Session Recommender Agent
a2a.agent.description=Recommends DevConf 2026 sessions based on attendee interests.
a2a.agent.version=1.0.0
a2a.agent.url=http://localhost:8080
```

Make sure Ollama is running with the Mistral model (see the main README for setup), then start the agent:

```bash
mvn quarkus:dev
```

You should see Quarkus start and the A2A server register the agent card.

---

## Step 8: Test Your Agent

### 8a. Fetch the AgentCard

```bash
curl -s http://localhost:8080/.well-known/agent.json | jq .
```

You should see your agent's card with its name, skills, and capabilities:

```json
{
  "name": "Session Recommender Agent",
  "description": "Recommends DevConf 2026 sessions...",
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
    }
  ],
  "capabilities": {
    "streaming": false,
    "pushNotifications": false
  }
}
```

### 8b. Send a Message

```bash
curl -s -X POST http://localhost:8080/a2a \
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
    "id": "test-1"
  }' | jq .
```

The response will be a JSON-RPC result containing a **Task** with status `COMPLETED` and an **Artifact** with the LLM's answer about AI-related sessions.

### 8c. Try More Queries

```bash
# Ask for personalized recommendations
curl -s -X POST http://localhost:8080/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "message/send",
    "params": {
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "I am a Java developer interested in microservices and cloud native. What should I attend?"}]
      }
    },
    "id": "test-2"
  }' | jq .result.artifacts[0].parts[0].text
```

---

## Checkpoint

At this point you should have:

- [x] A running Quarkus application on port 8080
- [x] An AgentCard served at `/.well-known/agent.json` with two skills
- [x] A working `message/send` endpoint that answers session questions
- [x] The LLM using tool calling to search real conference data (not hallucinating)

**You've built your first A2A agent.** It speaks a standard protocol that any A2A client — in any language, on any framework — can talk to. In the next exercise, you will prove this by deploying the same agent on two more runtimes.

---

> **Troubleshooting:**
>
> - **"Connection refused" on Ollama**: Make sure `podman-compose up -d` is running and the model has been pulled. Check with `curl http://localhost:11434/api/tags`.
> - **Empty responses**: The first LLM call can be slow while Ollama loads the model. Increase the timeout in `application.properties` if needed.
> - **"No sessions found"**: Verify the `session.data.path` points correctly to `conference-data/sessions.json` relative to the project directory.
