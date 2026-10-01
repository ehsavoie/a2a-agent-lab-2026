# Exercise 5: The Orchestrator & Concierge — Coordinating the Agent Mesh (25 min)

**Time:** 25 minutes

> _It is 8:15 AM on Day 1. Maya lands at the airport, but her flight was delayed by two hours. She opens her DevSphere app and types:_
>
> _"My flight was delayed so I missed the morning shuttle. I'm interested in agentic AI and Java agents — what talks should I catch today, how do I get to the venue quickly, and can you log my taxi receipt?"_
>
> _No single agent can answer this. You need an orchestrator._

## Overview

In this exercise you will build the **Orchestrator & Concierge** with two **Quarkus** modules. The Orchestrator is the primary edge router and user-facing gateway. The Concierge runs the specialist agents as tenants in one runtime. Together they will:

1. **Select** specialist agents from the Orchestrator's configured A2A clients
2. **Decompose** Maya's complex query into targeted sub-tasks using LangChain4j
3. **Dispatch** each sub-task to the appropriate Concierge tenant via A2A
4. **Aggregate** the responses into a single answer

The Concierge exposes separate AgentCards for its Schedule, Travel, Venue, and Expense tenants from one runtime. The Orchestrator uses those AgentCards to communicate with the specialist agents through A2A.

## Architecture

```
                                User (Maya)
                                    |
                          +---------v-----------+
                          | Orchestrator        |  REST :8090
                          | Quarkus             |
                          +---------+-----------+
                                    | A2A JSON-RPC
                          +---------v-----------+
                          | Concierge           |  Quarkus :8080
                          | Multi-tenant runtime|
                          | Schedule · Travel  |
                          | Venue · Expense   |
                          +--------------------+
```

### How Maya's request is resolved

1. **Edge Routing & Decomposition** — The Orchestrator receives the request at `/api/query`. Its LangChain4j supervisor selects from its configured A2A clients and splits the prompt into relevant sub-tasks.

2. **Session Matching (Schedule & Content Advisor)** — The Schedule tenant searches for sessions matching Maya's interest in agentic AI and Java agents.

3. **Travel & Transit (Travel & Logistics Agent)** — The Travel tenant determines the fastest route from the airport to the convention center and calculates an estimated arrival time.

4. **Venue Check (Venue & On-Site Operations Agent)** — If Maya asks about the venue, the Venue tenant can check real-time IoT seating sensors for session rooms.

5. **Expense Logging (Expense & Compliance Agent)** — The Expense tenant asks Maya for the receipt details needed to prepare an audit-ready entry.

---

## Step 1: Wire the Concierge Tenants

The Concierge hosts four specialist agents in a single Quarkus runtime. The A2A Java SDK uses CDI bean qualifiers (`@Tenant`) to associate each `AgentCard` and `AgentExecutor` with a named tenant, and routes incoming JSON-RPC requests to the right executor based on the URL path.

### 1a. Add the multitenancy dependency

Open `concierge/pom.xml` and uncomment (or add) the `a2a-java-extras-multitenancy` dependency. Without it the `@Tenant` qualifier is not on the classpath and the build will fail with unresolved imports:

```xml
<dependency>
    <groupId>org.a2aproject.sdk</groupId>
    <artifactId>a2a-java-extras-multitenancy</artifactId>
</dependency>
```

Then open each `*AgentCardProducer` class under `concierge/src/main/java/dev/devconf/` and look for the `// TODO` comments.

### 1b. Annotate the AgentCard producer

Add `@Tenant` with the agent's name to the `@Produces` method so the SDK registers the card and serves it at `/.well-known/<tenant>/agent-card.json`:

```java
@Produces
@Singleton
@PublicAgentCard
@Tenant("schedule")   // <-- add this
public AgentCard agentCard() { ... }
```

### 1c. Set the tenant name in the AgentInterface transport

The `AgentInterface` third argument tells the AgentCard which path suffix maps to this transport endpoint. It must match the tenant name:

```java
.supportedInterfaces(List.of(
    new AgentInterface(
        TransportProtocol.JSONRPC.asString(), agentUrl, "schedule") // <-- "schedule" here
))
```

This exposes the JSONRPC transport at `http://localhost:8080/schedule`.

### 1d. Annotate the AgentExecutor producer

Open each `*AgentExecutorProducer` class and add the same `@Tenant` annotation to its `@Produces` method:

```java
@Produces
@ApplicationScoped
@Tenant("schedule")   // <-- add this
public AgentExecutor agentExecutor() { ... }
```

Apply the same pattern for `"travel"`, `"venue"`, and `"expense"`. The tenant name must be identical across the card producer, the `AgentInterface` path suffix, and the executor producer.

---

## Step 2: Set Up the Orchestrator Project

Navigate to the Orchestrator project directory:

```bash
cd exercises/exercise-5-orchestrator
```

The project contains separate `orchestrator` and `concierge` Quarkus modules. The Orchestrator uses:
- Quarkus (Arc, REST, Jackson)
- LangChain4j with OpenAI GPT-6 Luna through the Responses API at medium reasoning effort
- LangChain4j Agentic support and A2A client transports

Check `orchestrator/src/main/resources/application.properties`:

```properties
quarkus.http.port=8090

# OpenAI Responses API
quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
quarkus.langchain4j.openai.chat-model.mode=responses
quarkus.langchain4j.openai.chat-model.model-name=gpt-6-luna
quarkus.langchain4j.openai.chat-model.reasoning-effort=medium

a2a.agent.url=http://localhost:8080
```

The four A2A client interfaces point to tenant AgentCards served by Concierge on port 8080:

| Agent | AgentCard URL |
|-------|---------------|
| Schedule & Content Advisor | `http://localhost:8080/.well-known/schedule/agent-card.json` |
| Travel & Logistics Agent | `http://localhost:8080/.well-known/travel/agent-card.json` |
| Venue & On-Site Operations | `http://localhost:8080/.well-known/venue/agent-card.json` |
| Expense & Compliance Agent | `http://localhost:8080/.well-known/expense/agent-card.json` |

Set an OpenAI API key with API billing enabled in the terminal used to start each module:

```bash
export OPENAI_API_KEY=your-api-key-here
```

ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.

---

## Step 3: Connect the Orchestrator to the Concierge

The Orchestrator declares one `@A2AClientAgent` interface per specialist. When the Concierge uses multi-tenancy, point `a2aServerUrl` at the Concierge base URL and set `tenant` to the name registered there. The SDK builds the AgentCard URL (`/<tenant>/agent-card.json`) automatically.

### 3a. Add `tenant` to ScheduleAdvisorA2AAgent, VenueA2AAgent, and ExpenseA2AAgent

Open each file — the `@A2AClientAgent` annotation is already there but the `tenant` attribute is missing. Add it:

```java
@A2AClientAgent(
        a2aServerUrl = "http://localhost:8080/",
        tenant = "schedule",   // <-- add this; "venue" / "expense" for the other two
        name = "Schedule & Content Advisor",
        description = "Answers questions about conference sessions, schedules, speakers, and talk content",
        outputKey = "schedule-response"
)
```

### 3b. Write the full `@A2AClientAgent` annotation for TravelA2AAgent, then register it with the supervisor

Open `orchestrator/src/main/java/dev/devconf/orchestrator/TravelA2AAgent.java`. The method signature is present but the annotation is entirely missing. Write it from scratch:

```java
@A2AClientAgent(
        a2aServerUrl = "http://localhost:8080/",
        tenant = "travel",
        name = "Travel & Logistics Agent",
        description = "Provides travel tips, transportation options, and logistics information for getting to the venue",
        outputKey = "travel-response"
)
ResultWithAgenticScope<String> ask(
        @V("query") String query,
        @A2AContextId @V("contextId") String contextId,
        @A2ATaskId @V("taskId") String taskId);
```

Once the annotation is in place, open `OrchestratorSupervisor.java` and add `TravelA2AAgent.class` to the `subAgents` list so the supervisor can use it:

```java
subAgents = {
        ScheduleAdvisorA2AAgent.class,
        VenueA2AAgent.class,
        TravelA2AAgent.class,   // <-- add this after completing the annotation
        ExpenseA2AAgent.class
}
```

You can verify the tenant cards are reachable after starting the Concierge:

```bash
curl -s http://localhost:8080/.well-known/schedule/agent-card.json | jq .
curl -s http://localhost:8080/.well-known/travel/agent-card.json | jq .
curl -s http://localhost:8080/.well-known/venue/agent-card.json | jq .
curl -s http://localhost:8080/.well-known/expense/agent-card.json | jq .
```

---

## Step 4: Orchestrator Supervisor

Open `orchestrator/src/main/java/dev/devconf/orchestrator/OrchestratorSupervisor.java`.

The LangChain4j `@SupervisorAgent` coordinates the fixed set of A2A client agents. Its system message describes how to route attendee requests, and its `subAgents` list names the Schedule, Venue, Travel, and Expense clients. The supervisor selects relevant agents for each request and summarizes their responses:

```java
@SupervisorAgent(
        name = "DevSphere Orchestrator",
        description = "Orchestrates specialist agents to answer complex, multi-domain questions about Devoxx Belgium 2026",
        outputKey = "response",
        responseStrategy = SupervisorResponseStrategy.SUMMARY,
        subAgents = {
                ScheduleAdvisorA2AAgent.class,
                VenueA2AAgent.class,
                TravelA2AAgent.class,
                ExpenseA2AAgent.class
        }
)
ResultWithAgenticScope<String> orchestrate(@V("request") String query);
```

For Maya's request, the supervisor can use the Schedule, Travel, and Expense agents. It can also use Venue when the request needs venue information. Adding another tenant requires adding its A2A client to the Orchestrator's configured sub-agents.

---

## Step 5: A2A Client Agents

Each client interface uses `@A2AClientAgent` to identify the specialist and its Concierge tenant. `TravelA2AAgent` is the one you write from scratch; the others already have the annotation but are missing the `tenant` attribute. Once complete, all four look like this:

```java
@A2AClientAgent(
        a2aServerUrl = "http://localhost:8080/",
        tenant = "travel",
        name = "Travel & Logistics Agent",
        description = "Provides travel tips, transportation options, and logistics information for getting to the venue",
        outputKey = "travel-response"
)
ResultWithAgenticScope<String> ask(
        @V("query") String query,
        @A2AContextId @V("contextId") String contextId,
        @A2ATaskId @V("taskId") String taskId);
```

The four specialist agents run as tenants in Concierge. LangChain4j uses the `tenant` value to resolve the AgentCard URL and sends each selected sub-task over A2A JSON-RPC.

---

## Step 6: The REST Request Flow

Open `orchestrator/src/main/java/dev/devconf/orchestrator/OrchestratorResource.java`.

The Orchestrator accepts a JSON request at `POST /api/query`, calls `OrchestratorSupervisor`, and returns the result in a JSON response:

```java
@Path("/api")
public class OrchestratorResource {

    @POST
    @Path("/query")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public QueryResponse query(QueryRequest request) {
        AgenticScope scope = supervisor.orchestrate(request.query()).agenticScope();
        String response = scope.readState("response").toString();
        return new QueryResponse(response);
    }

    public record QueryRequest(String query) {}
    public record QueryResponse(String response) {}
}
```

The Orchestrator's public request is REST. It uses A2A JSON-RPC for its calls to the Concierge tenant agents.

---

## Step 7: Wire and Test

### Start the Concierge and Orchestrator

Run these two services in separate terminals. Set `OPENAI_API_KEY` in both terminals. The Concierge serves all four specialist agents as tenants; the standalone services from Exercises 1–4 are not needed here.

| Terminal | Command | Service | Port |
|----------|---------|---------|------|
| Tab 1 | `cd exercises/exercise-5-orchestrator && mvn -pl concierge quarkus:dev` | Concierge (Schedule, Travel, Venue, and Expense tenants) | 8080 |
| Tab 2 | `cd exercises/exercise-5-orchestrator && mvn -pl orchestrator quarkus:dev` | **Orchestrator** | **8090** |

### Verify the Concierge tenant AgentCards

```bash
curl -s http://localhost:8080/.well-known/schedule/agent-card.json | jq .
curl -s http://localhost:8080/.well-known/travel/agent-card.json | jq .
curl -s http://localhost:8080/.well-known/venue/agent-card.json | jq .
curl -s http://localhost:8080/.well-known/expense/agent-card.json | jq .
```

### Send a simple query

The Orchestrator exposes a REST endpoint at `POST /api/query`:

```bash
curl -s -X POST http://localhost:8090/api/query \
  -H "Content-Type: application/json" \
  -d '{"query":"What agentic AI sessions are available on October 7?"}' | jq .
```

This should route to the Schedule & Content Advisor tenant.

### Send Maya's full query

```bash
curl -s --max-time 180 -X POST http://localhost:8090/api/query \
  -H "Content-Type: application/json" \
  -d '{"query":"My flight was delayed so I missed the morning shuttle. I am interested in agentic AI and Java agents — what talks should I catch today, how do I get to the venue quickly, and can you log my taxi receipt?"}' | jq .
```

Watch the Orchestrator's terminal output — you should see it:
1. Select relevant sub-agents from the configured set
2. Dispatch to the selected Concierge tenants via A2A
3. Aggregate the responses into a single coherent answer

The response should include session recommendations filtered for Maya's interests, travel directions, and an expense response.

---

## Checkpoint

You now have a **fully orchestrated agent mesh**:

```
Maya → Orchestrator REST (:8090) → Concierge A2A runtime (:8080) → Schedule + Travel + Venue + Expense tenants
```

The Orchestrator:
- Uses configured A2A client AgentCards for the four Concierge tenants
- Uses the LangChain4j supervisor to select agents for each query
- Dispatches sub-tasks to the selected agents via A2A
- Aggregates responses into a single coherent answer

This is the **power of A2A as a coordination protocol**: the Orchestrator doesn't know how any agent is implemented — it uses each configured AgentCard to learn what the agent can do and how to talk to it via A2A JSON-RPC.

### What's running now

| Agent | Technology | Port |
|-------|-----------|------|
| Schedule, Travel, Venue, and Expense tenants | Quarkus Concierge runtime | 8080 |
| **Orchestrator** | **Quarkus REST API** | **8090** |

---

> **Discussion: Could the Orchestrator Use Quarkus Native?**
>
> The Orchestrator is the edge router — every user request hits it first. A native build of Quarkus can provide:
> - **Sub-second cold starts** — critical for auto-scaling in serverless or Kubernetes
> - **Minimal memory footprint** — the Orchestrator coordinates but doesn't do heavy computation
> - **Fast request routing** — native compilation eliminates JIT warmup
>
> The specialist agents are hosted as tenants in the Concierge Quarkus runtime on port 8080. The Orchestrator may scale up/down based on load.
