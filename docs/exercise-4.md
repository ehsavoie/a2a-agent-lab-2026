# Exercise 4: The Concierge — Orchestrating the Agent Mesh (25 min)

> _"An attendee asks: 'I'm arriving Thursday afternoon — what sessions should I attend, and where should I eat nearby afterward?' No single agent can answer this. You need an orchestrator — the Concierge — that decomposes complex queries, discovers available agents, dispatches sub-tasks, and assembles a coherent response."_

## Overview

In this exercise you will build the **Concierge Agent** — a dynamic router and orchestrator that ties the entire mesh together. It will:

1. **Discover** available agents by fetching their AgentCards
2. **Decompose** complex user queries into targeted sub-tasks
3. **Dispatch** each sub-task to the appropriate specialist agent via A2A
4. **Aggregate** all responses into a single coherent answer

This is where the A2A protocol truly shines: the Concierge doesn't need to know the internals of any agent — it only needs their AgentCard.

## Architecture

```
                    ┌─────────────────────────────────┐
    User query ───► │       Concierge Agent (:8090)   │
                    │                                 │
                    │  1. QueryDecomposer (LLM)       │
                    │     "sessions + dinner" →       │
                    │       SubTask[session-agent]     │
                    │       SubTask[travel-agent]      │
                    │                                 │
                    │  2. Dispatch via A2A             │
                    │     ┌──────────┐ ┌────────────┐ │
                    │     │ Session  │ │   Travel   │ │
                    │     │  Agent   │ │   Tips     │ │
                    │     │  :8080   │ │   :9000    │ │
                    │     └──────────┘ └────────────┘ │
                    │                                 │
                    │  3. ResponseAggregator (LLM)    │
                    │     Combine into one answer     │
                    └────────────────┬────────────────┘
                                     │
                    Final answer ◄───┘
```

---

## Step 1: Create the Concierge Project

Navigate to the Concierge project directory:

```bash
cd exercises/exercise-4-concierge
```

The `pom.xml` is already set up with the same stack as Exercise 1:
- Quarkus (Arc, REST, Jackson)
- LangChain4j with Ollama
- A2A Java SDK (server + client)

Check `src/main/resources/application.properties`:

```properties
quarkus.http.port=8090

# Ollama
quarkus.langchain4j.ollama.base-url=http://localhost:11434
quarkus.langchain4j.ollama.chat-model.model-id=mistral

# Agent URLs for discovery
concierge.agent-urls=http://localhost:8080,http://localhost:9000

# A2A Agent Identity
a2a.agent.name=DevConf Concierge
a2a.agent.url=http://localhost:8090
```

Notice the `concierge.agent-urls` property — this lists the agents the Concierge will discover. In production, you'd use a service registry; for the lab, static URLs keep things simple.

---

## Step 2: Agent Discovery Service

Open `src/main/java/dev/devconf/concierge/AgentDiscoveryService.java`.

This service fetches and caches the AgentCard from each known agent URL:

```java
@ApplicationScoped
public class AgentDiscoveryService {

    @ConfigProperty(name = "concierge.agent-urls")
    List<String> agentUrls;

    private final Map<String, AgentInfo> registry = new HashMap<>();

    @PostConstruct
    void discoverAgents() {
        for (String url : agentUrls) {
            // Fetch AgentCard from /.well-known/agent.json
            AgentCard card = fetchAgentCard(url);
            List<String> skills = card.skills().stream()
                .map(AgentSkill::name).toList();

            registry.put(card.name(), new AgentInfo(
                card.name(), url, skills, card.description()
            ));
        }
    }
    ...
}
```

The discovery pattern:
1. For each known URL, HTTP GET `/.well-known/agent.json`
2. Parse the AgentCard to learn the agent's name, skills, and capabilities
3. Store in a registry for fast lookup during query routing

The `buildSkillsSummary()` method formats the registry for the LLM prompt:

```
Available agents:
- Session Recommender Agent (http://localhost:8080):
  Skills: Session Search, Session Recommendations
- Travel Tips Agent (http://localhost:9000):
  Skills: Restaurant Search, Transit Information, Local Tips
```

> **Production note**: In a real deployment, you would use a service registry like Consul, etcd, or DNS-based discovery. Agents would register themselves on startup and deregister on shutdown. The Concierge would watch the registry for changes.

---

## Step 3: Query Decomposer

Open `src/main/java/dev/devconf/concierge/QueryDecomposer.java`.

This is a LangChain4j AI Service that uses the LLM to split a complex user query into targeted sub-tasks:

```java
@RegisterAiService
public interface QueryDecomposer {

    @SystemMessage("""
        You decompose complex user queries into sub-tasks for specialist agents.

        Available agents:
        {{agentsSummary}}

        For each sub-task, specify:
        - agentName: the exact name of the target agent
        - query: a focused question for that agent

        Return ONLY a JSON array. Example:
        [{"agentName": "Session Agent", "query": "AI talks on Thursday"}]
        ...
    """)
    String decompose(@UserMessage String query,
                     @V("agentsSummary") String agentsSummary);
}
```

The critical design choice here is **prompt engineering**: the system message includes the list of available agent skills so the LLM knows what agents exist and what each one can do. This makes routing dynamic — add a new agent to the mesh and the Concierge automatically learns to route to it.

The result is parsed into `SubTask` records:

```java
public record SubTask(String agentName, String query) {}
```

---

## Step 4: Response Aggregator

Open `src/main/java/dev/devconf/concierge/ResponseAggregator.java`.

After all sub-tasks return, this AI Service synthesizes the responses:

```java
@RegisterAiService
public interface ResponseAggregator {

    @SystemMessage("""
        You synthesize responses from multiple specialist agents into a
        single coherent, helpful answer for a conference attendee.

        Organize the information logically. If one agent failed to respond,
        acknowledge it gracefully and continue with what you have.
        Maintain a friendly, conversational tone.
    """)
    String aggregate(@UserMessage String combinedResponses);
}
```

The aggregator receives a formatted string with each agent's response and produces a unified answer the user sees.

---

## Step 5: The Orchestration Loop

Open `src/main/java/dev/devconf/concierge/ConciergeAgentExecutorProducer.java`.

This is where everything comes together. The `AgentExecutor` implements this flow:

```
User message
    │
    ▼
QueryDecomposer.decompose(query, agentsSummary)
    │
    ▼
[SubTask("Session Agent", "Thursday AI sessions"),
 SubTask("Travel Tips Agent", "dinner near venue")]
    │
    ▼
For each SubTask:
    → Build A2A message
    → Send to agent via HTTP (message/send)
    → Collect response text
    │
    ▼
ResponseAggregator.aggregate(allResponses)
    │
    ▼
Return final answer as A2A Task artifact
```

Key sections of the code:

**1. Decompose the query:**
```java
String agentsSummary = discoveryService.buildSkillsSummary();
String decomposed = queryDecomposer.decompose(userText, agentsSummary);
List<SubTask> subTasks = parseSubTasks(decomposed);
```

**2. Dispatch to agents:**
```java
for (SubTask subTask : subTasks) {
    AgentInfo agent = discoveryService.getAgentByName(subTask.agentName());
    // Send A2A message/send request
    String response = sendA2AMessage(agent.url(), subTask.query());
    responses.add(agent.name() + " responded:\n" + response);
}
```

**3. Aggregate and respond:**
```java
String combined = String.join("\n\n---\n\n", responses);
String finalAnswer = responseAggregator.aggregate(combined);
emitter.addArtifact(List.of(new TextPart(finalAnswer)));
emitter.complete();
```

---

## Step 6: Wire and Test

### Start all agents

Make sure you have these agents running from previous exercises:

| Terminal | Command | Agent | Port |
|----------|---------|-------|------|
| Tab 1 | `cd exercises/exercise-1-session-agent && mvn quarkus:dev` | Session Agent | 8080 |
| Tab 2 | `cd exercises/exercise-3-travel-agent-python && python travel_agent.py` | Travel Tips | 9000 |
| Tab 3 | `cd exercises/exercise-4-concierge && mvn quarkus:dev` | **Concierge** | **8090** |

### Verify the Concierge's AgentCard

```bash
curl -s http://localhost:8090/.well-known/agent.json | python -m json.tool
```

You should see the Concierge's AgentCard with the skill `general-conference-assistant`.

### Send a simple query

```bash
curl -s -X POST http://localhost:8090/a2a \
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
    "id": "test-simple"
  }' | python -m json.tool
```

This should route entirely to the Session Agent.

### Send a complex, multi-agent query

```bash
curl -s -X POST http://localhost:8090/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "message/send",
    "params": {
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "I am a Java developer arriving Thursday. What sessions should I attend, and where should I have dinner afterward?"}]
      }
    },
    "id": "test-complex"
  }' | python -m json.tool
```

Watch the Concierge's terminal output — you should see it:
1. Decompose the query into two sub-tasks (sessions + dinner)
2. Dispatch to Session Agent and Travel Tips Agent
3. Aggregate the responses into a single coherent answer

The response should include both session recommendations AND restaurant suggestions — information that no single agent could provide alone.

---

## Checkpoint

You now have a **fully orchestrated agent mesh**:

```
User → Concierge (:8090) → Session Agent (:8080) + Travel Tips Agent (:9000)
```

The Concierge:
- Discovers agents dynamically via AgentCard fetching
- Uses the LLM to decompose complex queries into targeted sub-tasks
- Dispatches sub-tasks to the right agent via A2A
- Aggregates responses into a single coherent answer

This is the **power of A2A as a coordination protocol**: the Concierge doesn't know how any agent is implemented — it only knows what each agent can do (from the AgentCard) and how to talk to it (via A2A JSON-RPC).

### What's running now

| Agent | Language | Framework | Port |
|-------|----------|-----------|------|
| Session Agent | Java | Quarkus | 8080 |
| Session Agent | Java | Spring Boot | 8081 |
| Session Agent | Java | WildFly | 8082 |
| Travel Tips Agent | Python | a2a-sdk | 9000 |
| **Concierge** | **Java** | **Quarkus** | **8090** |

Keep the agents on ports 8080, 9000, and 8090 running for Exercise 5.
