# Exercise 4: The Expense & Compliance Agent

**Time:** 20 minutes

> _"Maya's taxi from the airport cost $45. She snaps a photo of the receipt in the DevSphere app. The Travel Agent extracts the fare details and passes the structured payload to the Expense & Compliance Agent — which validates the amount against corporate policy, logs an audit-ready reimbursement entry, and confirms it all in seconds."_

## Overview

In this exercise you will:

1. Build the **Expense & Compliance Agent** — a WildFly 41 (Jakarta EE / Java A2A SDK) agent that standardizes receipts into audit-ready expense logs
2. Wire up **cross-agent data handoff** — the Travel Agent extracts receipt details, the Orchestrator routes them to the Expense Agent
---

## Part 1: Build the Expense & Compliance Agent

The agent uses `gpt-6-luna` through the OpenAI Responses API at medium reasoning effort. Create an OpenAI API key with API billing enabled before starting it. ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.

```bash
export OPENAI_API_KEY=your-api-key-here
```

Navigate to the exercise directory:

```bash
cd exercises/exercise-4-expense-agent
```

This is another **WildFly 41 + Java A2A SDK** agent — the same pattern you learned in Exercise 1 with the Schedule Advisor.

### Step 1: The ExpenseTool

Open `expense-agent/src/main/java/dev/devconf/expense/ExpenseTool.java`. This tool gives the LLM access to expense management operations:

```java
public class ExpenseTool {

    private static final Map<String, Double> CATEGORY_LIMITS = Map.of(
            "Meals", 75.0,
            "Transportation", 200.0,
            "Accommodation", 350.0,
            "Registration", 500.0,
            "Supplies", 50.0);

    private final List<Expense> expenseLog = new ArrayList<>();

    @Tool("Log and validate an expense entry against corporate compliance rules.")
    public String logExpense(String vendor, String amount, String currency, String date,
                             String category, String description) {
        // Validate category, parse amount, check against limits
        // Add to in-memory log, return confirmation with expense ID
    }

    @Tool("Process structured receipt data received from other agents.")
    public String processReceipt(String receiptData) {
        // Extract vendor, amount, date, category from text
        // Delegate to logExpense()
    }

    @Tool("Get a summary of all logged expenses, grouped by category.")
    public String getExpenseSummary(String attendeeName) { ... }

    @Tool("Check overall compliance status: lists any flagged items.")
    public String checkComplianceStatus() { ... }
}
```

Four tools are exposed to the LLM:
- **`logExpense`** — Validates and logs individual expenses with compliance checks
- **`processReceipt`** — Accepts structured text from other agents (cross-agent handoff)
- **`getExpenseSummary`** — Totals by category for reporting
- **`checkComplianceStatus`** — Flags any over-limit entries

> **Key design:** The `processReceipt` tool is specifically designed for **cross-agent data handoff**. When the Travel Agent extracts receipt details, the Orchestrator can pass that structured text directly to this tool.

### Step 2: The ExpenseService

The LangChain4j AI Service interface:

```java
public interface ExpenseService {

    @SystemMessage("""
            You are the DevConf 2026 Expense & Compliance Agent.
            You standardize receipts into
            corporate audit-ready expense logs.
            Validate expenses against compliance rules:
            - Meals: max $75/day, Transportation: max $200/trip
            All expenses require: vendor, amount, currency, date, category.
            """)
    String chat(@UserMessage String userMessage);
}
```

Wired programmatically in `ExpenseServiceProducer` — same CDI pattern as Exercise 1:

```java
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import java.time.Duration;

@ApplicationScoped
public class ExpenseServiceProducer {

    @ConfigProperty(name = "openai.api-key")
    String openAiApiKey;

    @ConfigProperty(name = "openai.model-name", defaultValue = "gpt-6-luna")
    String openAiModelName;

    @PostConstruct
    void init() {
        OpenAiResponsesChatModel chatModel = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(HttpClientBuilderLoader.loadHttpClientBuilder()
                        .readTimeout(Duration.ofSeconds(120)))
                .apiKey(openAiApiKey)
                .modelName(openAiModelName)
                .reasoningEffort("medium")
                .build();

        expenseService = AiServices.builder(ExpenseService.class)
                .chatModel(chatModel)
                .tools(new ExpenseTool())
                .build();
    }
}
```

### Step 3: AgentCard and AgentExecutor

The AgentCard advertises three skills:

```java
AgentSkill.builder()
    .id("expense-logging")
    .name("Expense Logging")
    .description("Log and validate expense entries against corporate compliance rules.")
    .build(),
AgentSkill.builder()
    .id("receipt-processing")
    .name("Receipt Processing")
    .description("Process receipt data from other agents into audit-ready entries.")
    .build(),
AgentSkill.builder()
    .id("compliance-report")
    .name("Compliance Report")
    .description("Generate compliance status reports and flag policy violations.")
    .build()
```

The `ExpenseAgentExecutorProducer` follows the identical pattern from Exercise 1.

### Step 4: Build and Run

```bash
cd exercises/exercise-4-expense-agent

# Build the A2A WAR and provision WildFly
mvn package

# If expense-agent/target/wildfly already exists, refresh its deployed WAR
cp expense-agent/target/expense-agent-1.0.0-SNAPSHOT.war expense-agent/target/wildfly/standalone/deployments/ROOT.war

# Start WildFly with port offset (HTTP on 8082)
./expense-agent/target/wildfly/bin/standalone.sh -Djboss.socket.binding.port-offset=2
```

### Step 5: Test the Expense Agent

**Fetch the AgentCard:**

```bash
curl -s http://localhost:8082/.well-known/agent-card.json | jq .
```

You should see skills: `expense-logging`, `receipt-processing`, `compliance-report`.

**Log an expense:**

```bash
curl -s -X POST http://localhost:8082/ \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "msg-1",
        "role": "ROLE_USER",
        "parts": [{"text": "Log a $45 taxi from Airport Express Cabs on 2026-10-07 for transportation to the convention center"}]
      }
    },
    "id": "test-expense-1"
  }' | jq .result.task.artifacts[0].parts[0].text
```

**Process a receipt (cross-agent format):**

```bash
curl -s -X POST http://localhost:8082/ \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "msg-1",
        "role": "ROLE_USER",
        "parts": [{"text": "Process this receipt:\nvendor: Airport Express Cabs\namount: $45.00\ncurrency: USD\ndate: 2026-10-07\ncategory: Transportation\ndescription: Taxi from airport to convention center"}]
      }
    },
    "id": "test-receipt-1"
  }' | jq .
```

---

## Part 2: Cross-Agent Data Handoff

This is where A2A's power as a coordination protocol shines. The Expense Agent doesn't just work in isolation — it receives structured data from other agents in the mesh.

### The Flow: Maya's Taxi Receipt

```
Maya uploads receipt photo
    │
    ▼
Orchestrator (:8090)
    │ Decomposes: "log this receipt"
    │ Routes to Expense Agent
    ▼
Expense Agent (:8082)
    │ processReceipt() validates & logs
    │ Returns audit-ready entry
    ▼
Orchestrator aggregates confirmation
    │
    ▼
Maya sees: "Your $45 taxi expense has been logged (ID: EXP-A1B2C3D4). Compliant ✓"
```

In a production system, the Travel Agent would extract receipt details (OCR / structured extraction), then the Orchestrator would pass that structured data to the Expense Agent via A2A. The `receipt-processing` skill is specifically designed for this cross-agent handoff pattern.

### Connect the Orchestrator

The Exercise 5 Expense client targets the Expense tenant AgentCard at `http://localhost:8080/.well-known/expense/agent-card.json`, served by the Concierge module. For Exercise 5, start Concierge with `cd exercises/exercise-5-orchestrator && mvn -pl concierge quarkus:dev`, then start the Orchestrator. The standalone Expense service on port 8082 is used for this exercise's own checks, not by Exercise 5.

Then test the full flow:

```bash
curl -s -X POST http://localhost:8090/api/query \
  -H "Content-Type: application/json" \
  -d '{"query": "I took a $45 taxi from Airport Express Cabs to the convention center on 2026-10-07. Can you log this as an expense?"}' | jq .
```

The Orchestrator should route this request to the Expense tenant.

---

## Distributed State & Load Balancing (Discussion)

### The Problem

The Orchestrator handles multi-turn conversations. An attendee might ask:
1. "What AI sessions are on Thursday?" → routes to Schedule Advisor
2. "Tell me more about the second one" → needs to remember which sessions were listed

### The A2A Solution: `contextId`

A2A Tasks include a `contextId` field that groups related messages into a conversation:

```java
String contextId = context.getTask().contextId();
```

In production, replace an in-memory task store with Redis or PostgreSQL.

### Load Balancing Tradeoffs

| Approach | Pros | Cons |
|----------|------|------|
| **Sticky sessions** (route by contextId) | Simple, no shared state needed | Uneven load, lost state on replica failure |
| **Shared state store** (Redis/DB) | Any replica can serve any request | Adds latency, another dependency to operate |
| **Hybrid** | Best of both | More complex to implement |

For most A2A deployments, **shared state store + any-replica routing** is the recommended pattern.

---

## Checkpoint

You now have:

- [x] **Expense & Compliance Agent** running on port 8082 with three skills
- [x] **Cross-agent data handoff** — receipt data flows from Travel Agent to Expense Agent

---

## The Complete DevSphere System

```
┌──────────────────────────┐      A2A JSON-RPC      ┌──────────────────────────┐
│ Orchestrator              │ ─────────────────────► │ Concierge runtime         │
│ Quarkus REST :8090        │                         │ Quarkus :8080              │
│ POST /api/query           │                         │ Schedule · Travel tenants │
└──────────────────────────┘                         │ Venue · Expense tenants  │
                                                     └──────────────────────────┘
```

## What You Built Today

In two hours, you built a **production-grade A2A agent ecosystem**:

1. **Exercise 1** — Your first A2A agent: the Schedule & Content Advisor (Quarkus + A2A Java SDK), teaching AgentCard, AgentExecutor, and LLM tool calling
2. **Exercise 2** — The Venue & On-Site Operations Agent (Spring Boot + LangChain4j), proving A2A is runtime-agnostic
3. **Exercise 3** — The Travel & Logistics Agent (Python), proving A2A is language-independent
4. **Exercise 4** — The Expense & Compliance Agent (Java A2A SDK) for cross-agent data handoff
5. **Exercise 5** — The Quarkus Orchestrator routes requests to Schedule, Travel, Venue, and Expense tenants hosted in one Concierge runtime

### Going Further

- **Security**: Add OAuth2/OIDC authentication to your agents using Keycloak
- **Streaming**: Enable SSE streaming for real-time responses (`StreamMessage` instead of `SendMessage`)
- **Push Notifications**: Register webhooks so agents can proactively notify each other
- **Service Registry**: Replace hardcoded URLs with Consul or Kubernetes service discovery
- **MCP Integration**: Give agents access to external tools via MCP

### Resources

- [A2A Protocol Specification](https://google.github.io/A2A/)
- [A2A Java SDK](https://github.com/a2aproject/a2a-java)
- [A2A Jakarta EE SDK (Java A2A SDK)](https://github.com/wildfly-extras/a2a-jakarta)
- [A2A Python SDK](https://github.com/a2aproject/a2a-python)
- [A2A Samples](https://github.com/a2aproject/a2a-samples)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)

---

> **Troubleshooting:**
>
> - **Authentication or billing error**: Check `OPENAI_API_KEY` and confirm API billing is enabled. ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.
> - **Empty responses**: The first LLM call can be slow. Increase the OpenAI HTTP read timeout in `ExpenseServiceProducer` if needed.
> - **Port 8082 conflict**: Make sure you're starting WildFly with `-Djboss.socket.binding.port-offset=2`.
