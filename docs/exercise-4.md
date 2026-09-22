# Exercise 4: The Orchestrator & Concierge — Coordinating the Agent Mesh (25 min)

**Time:** 25 minutes

> _It is 8:15 AM on Day 1. Maya lands at the airport, but her flight was delayed by two hours. She opens her DevSphere app and types:_
>
> _"My flight was delayed so I missed the morning shuttle. I'm interested in agentic AI and Java agents — what talks should I catch today, how do I get to the venue quickly, and can you log my taxi receipt?"_
>
> _No single agent can answer this. You need an orchestrator._

## Overview

In this exercise you will build the **Orchestrator & Concierge** — the primary edge router and user-facing gateway, built with **Quarkus Native** for sub-second cold starts and minimal memory overhead. It will:

1. **Discover** available agents by fetching their AgentCards across the mesh
2. **Decompose** Maya's complex query into targeted sub-tasks using LangChain4j
3. **Dispatch** each sub-task to the appropriate specialist agent via A2A
4. **Aggregate** all responses into a single coherent answer

This is where the A2A protocol truly shines: the Orchestrator doesn't need to know the internals of any agent — it only needs their AgentCard.

## Architecture

```
                                User (Maya)
                                    |
                          +---------v-----------+
                          |   DevSphere         |  Port 8090 (Quarkus)
                          |   Orchestrator      |
                          +---------+-----------+
                                    |
              +----------+----------+----------+---------+
              |          |                     |         |
     +--------v---+ +----v--------+  +---------v--+ +---v-----------+
     | Schedule & | | Venue &     |  | Travel &   | | Expense &     |
     | Content    | | On-Site Ops |  | Logistics  | | Compliance    |
     | Advisor    | | (Spring     |  | (Python)   | | (Jakarta EE)  |
     | (Java A2A) | |  Boot)      |  | :9000      | | :8082         |
     | :8080      | | :8081       |  +------------+ +---------------+
     +------------+ +-------------+
```

### How Maya's request is resolved

1. **Edge Routing & Decomposition** — The Orchestrator receives the request, parses the intent using LangChain4j, inspects the active AgentCard registry, and splits the prompt into four sub-tasks.

2. **Session Matching (Schedule & Content Advisor)** — Knowing Maya will arrive around 9:45 AM, the Jakarta EE agent filters out early morning sessions and cross-references her interest in agentic AI and Java agents with the schedule.

3. **Travel & Transit (Travel & Logistics Agent)** — The Python agent determines the fastest route from the airport to the convention center and calculates an estimated arrival time.

4. **Venue Check (Venue & On-Site Operations Agent)** — The Spring Boot agent checks real-time IoT seating sensors for the recommended session rooms.

5. **Expense Logging (Expense & Compliance Agent)** — The Jakarta EE agent prepares an audit-ready entry for Maya's taxi receipt.

---

## Step 1: Create the Orchestrator Project

Navigate to the Orchestrator project directory:

```bash
cd exercises/exercise-4-orchestrator
```

The `pom.xml` is set up with the same Quarkus stack used across the lab:
- Quarkus (Arc, REST, Jackson)
- LangChain4j with Ollama
- A2A Java SDK (server + client)

Check `src/main/resources/application.properties`:

```properties
quarkus.http.port=8090

# Ollama
quarkus.langchain4j.ollama.base-url=http://localhost:11434
quarkus.langchain4j.ollama.chat-model.model-id=granite4:350m

# Agent URLs for discovery
orchestrator.agent-urls=http://localhost:8080,http://localhost:8081,http://localhost:9000,http://localhost:8082

# A2A Agent Identity
a2a.agent.name=DevSphere Orchestrator
a2a.agent.url=http://localhost:8090
```

Notice the `orchestrator.agent-urls` property — this lists the agents the Orchestrator will discover. In production, you'd use a service registry; for the lab, static URLs keep things simple.

---

## Step 2: Agent Discovery Service

Open `src/main/java/dev/devconf/orchestrator/AgentDiscoveryService.java`.

This service fetches and caches the AgentCard from each known agent URL:

```java
@ApplicationScoped
public class AgentDiscoveryService {

    @ConfigProperty(name = "orchestrator.agent-urls")
    List<String> agentUrls;

    private final Map<String, AgentInfo> registry = new LinkedHashMap<>();

    public record AgentInfo(String name, String url,
                            List<String> skills, String description) {}

    @PostConstruct
    void discoverAgents() {
        for (String baseUrl : agentUrls) {
            // Fetch AgentCard from /.well-known/agent-card.json
            String cardUrl = baseUrl + "/.well-known/agent-card.json";
            HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(cardUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

            JsonNode card = mapper.readTree(response.body());
            String name = card.path("name").asText();
            List<String> skills = /* parse skills from card */;
            registry.put(name, new AgentInfo(name, baseUrl, skills, ...));
        }
    }
}
```

The discovery pattern:
1. For each known URL, HTTP GET `/.well-known/agent-card.json`
2. Parse the AgentCard to learn the agent's name, skills, and capabilities
3. Store in a registry for fast lookup during query routing

The `buildSkillsSummary()` method formats the registry for the LLM prompt:

```
Agent "Schedule & Content Advisor" (http://localhost:8080)
  Description: Deep-scans the summit's session catalog...
  Skills:
    - Session Search: Search for sessions by topic, speaker, or track
    - Session Recommendations: Get personalized recommendations
    - Speaker Info: Look up speaker bios and expertise

Agent "Travel & Logistics Agent" (http://localhost:9000)
  Description: Connects to flight APIs, local transit, and hotel systems
  Skills:
    - Flight Status: Check flight status and delays
    - Transit Routes: Get directions to the convention center
```

> **Production note**: In a real deployment, replace the static URL list with a service registry like Consul, etcd, or Kubernetes DNS. Agents register on startup and deregister on shutdown. The Orchestrator watches the registry for changes.

---

## Step 3: Query Decomposer

Open `src/main/java/dev/devconf/orchestrator/QueryDecomposer.java`.

This LangChain4j AI Service uses the LLM to split a complex user query into targeted sub-tasks:

```java
@RegisterAiService
public interface QueryDecomposer {

    @SystemMessage("""
        You are a query decomposition engine for the DevSphere
        conference assistant.

        Available agents and their capabilities:
        {{agentsSummary}}

        For each sub-task, specify:
        - agentName: the EXACT name of the target agent
        - query: a focused question for that agent

        Return ONLY a valid JSON array:
        [{"agentName": "...", "query": "..."}]
        """)
    String decompose(@UserMessage String query,
                     @V("agentsSummary") String agentsSummary);
}
```

The critical design choice: **the system message includes the list of available agent skills** so the LLM knows what agents exist and what each one can do. This makes routing dynamic — add a new agent to the mesh and the Orchestrator automatically learns to route to it.

For Maya's query, the decomposer produces:

```json
[
  {"agentName": "Schedule & Content Advisor",
   "query": "What sessions about agentic AI and Java agents are available today after 9:45 AM?"},
  {"agentName": "Travel & Logistics Agent",
   "query": "What is the fastest way from the airport to the convention center right now?"},
  {"agentName": "Expense & Compliance Agent",
   "query": "Prepare to log a taxi receipt for travel from the airport to the venue."}
]
```

The result is parsed into `SubTask` records:

```java
public record SubTask(String agentName, String query) {}
```

---

## Step 4: Response Aggregator

Open `src/main/java/dev/devconf/orchestrator/ResponseAggregator.java`.

After all sub-tasks return, this AI Service synthesizes the responses:

```java
@RegisterAiService
public interface ResponseAggregator {

    @SystemMessage("""
        You synthesize responses from multiple specialist systems into
        a single coherent, helpful answer for a conference attendee.

        Organize by topic, use clear sections, maintain a warm tone.
        Do NOT mention internal agent names.
        """)
    String aggregate(@UserMessage String combinedResponses);
}
```

The aggregator receives a formatted string with each agent's response and produces a unified answer Maya sees in her DevSphere app.

---

## Step 5: The Orchestration Loop

Open `src/main/java/dev/devconf/orchestrator/OrchestratorAgentExecutorProducer.java`.

This is where everything comes together. The `AgentExecutor` implements this flow:

```
Maya's message
    │
    ▼
QueryDecomposer.decompose(query, agentsSummary)
    │
    ▼
[SubTask("Schedule & Content Advisor", "sessions about agentic AI..."),
 SubTask("Travel & Logistics Agent", "fastest route from airport..."),
 SubTask("Expense & Compliance Agent", "log taxi receipt...")]
    │
    ▼
For each SubTask:
    → Build A2A message
    → Send to agent via HTTP (SendMessage)
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
String skillsSummary = discoveryService.buildSkillsSummary();
String decomposedJson = queryDecomposer.decompose(userText, skillsSummary);
List<SubTask> subTasks = parseSubTasks(decomposedJson);
```

**2. Dispatch to agents:**
```java
for (SubTask subTask : subTasks) {
    var agentInfo = discoveryService.getAgentByName(subTask.agentName());
    if (agentInfo.isPresent()) {
        String response = dispatchToAgent(agentInfo.get().url(), subTask.query());
        agentResponses.add("=== " + subTask.agentName() + " ===\n" + response);
    } else {
        agentResponses.add("=== " + subTask.agentName()
                + " ===\n[Agent not found in registry]");
    }
}
```

**3. Aggregate and respond:**
```java
String combined = "Original question: " + userText + "\n\n"
        + String.join("\n\n", agentResponses);
String finalAnswer = responseAggregator.aggregate(combined);
emitter.addArtifact(List.of(new TextPart(finalAnswer)));
emitter.complete();
```

The `dispatchToAgent` method sends a standard A2A JSON-RPC `SendMessage` request to each specialist agent and extracts the text artifact from the response. It handles both `/a2a` (Java agents) and root `/` (Python agent) endpoints.

---

## Step 6: Wire and Test

### Start all agents

Make sure you have these agents running from previous exercises:

| Terminal | Command | Agent | Port |
|----------|---------|-------|------|
| Tab 1 | `cd exercises/exercise-1-schedule-advisor && ...` | Schedule & Content Advisor | 8080 |
| Tab 2 | `cd exercises/exercise-2-venue-agent && mvn spring-boot:run` | Venue & On-Site Ops | 8081 |
| Tab 3 | `cd exercises/exercise-3-travel-agent && python travel_agent.py` | Travel & Logistics | 9000 |
| Tab 4 | `cd exercises/exercise-5-expense-agent && ...` | Expense & Compliance | 8082 |
| Tab 5 | `cd exercises/exercise-4-orchestrator && mvn quarkus:dev` | **Orchestrator** | **8090** |

### Verify the Orchestrator's AgentCard

```bash
curl -s http://localhost:8090/.well-known/agent-card.json | jq .
```

You should see the Orchestrator's AgentCard with the skill `general-conference-assistant`.

### Send a simple query

```bash
curl -s -X POST http://localhost:8090/ \
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
    "id": "test-simple"
  }' | jq .
```

This should route entirely to the Schedule & Content Advisor.

### Send Maya's full query

```bash
curl -s --max-time 180 -X POST http://localhost:8090/ \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "msg-1",
        "role": "ROLE_USER",
        "parts": [{"text": "My flight was delayed so I missed the morning shuttle. I am interested in agentic AI and Java agents — what talks should I catch today, how do I get to the venue quickly, and can you log my taxi receipt?"}]
      }
    },
    "id": "maya-flight-delay"
  }' | jq .
```

Watch the Orchestrator's terminal output — you should see it:
1. Decompose the query into three or four sub-tasks
2. Dispatch to the Schedule Advisor, Travel Agent, and Expense Agent
3. Aggregate the responses into a single coherent answer

The response should include session recommendations filtered for Maya's interests, travel directions, and a confirmation that her expense will be logged — information that no single agent could provide alone.

---

## Checkpoint

You now have a **fully orchestrated agent mesh**:

```
Maya → Orchestrator (:8090) → Schedule (:8080) + Venue (:8081) + Travel (:9000) + Expense (:8082)
```

The Orchestrator:
- Discovers agents dynamically via AgentCard fetching
- Uses the LLM to decompose complex queries into targeted sub-tasks
- Dispatches sub-tasks to the right agent via A2A
- Aggregates responses into a single coherent answer

This is the **power of A2A as a coordination protocol**: the Orchestrator doesn't know how any agent is implemented — it only knows what each agent can do (from the AgentCard) and how to talk to it (via A2A JSON-RPC).

### What's running now

| Agent | Technology | Port |
|-------|-----------|------|
| Schedule & Content Advisor | Jakarta EE / Java A2A SDK | 8080 |
| Venue & On-Site Ops | Spring Boot + LangChain4j | 8081 |
| Travel & Logistics | Python A2A SDK | 9000 |
| Expense & Compliance | Jakarta EE / Java A2A SDK | 8082 |
| **Orchestrator & Concierge** | **Quarkus Native** | **8090** |

---

> **Discussion: Why Quarkus Native for the Orchestrator?**
>
> The Orchestrator is the edge router — every user request hits it first. Quarkus Native gives you:
> - **Sub-second cold starts** — critical for auto-scaling in serverless or Kubernetes
> - **Minimal memory footprint** — the Orchestrator coordinates but doesn't do heavy computation
> - **Fast request routing** — native compilation eliminates JIT warmup
>
> The specialist agents can run on heavier runtimes (WildFly, Spring Boot) because they start once and stay running. The Orchestrator may scale up/down based on load.
