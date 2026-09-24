# Exercise 4: The Expense & Compliance Agent + Enterprise Observability

**Time:** 20 minutes

> _"Maya's taxi from the airport cost $45. She snaps a photo of the receipt in the DevSphere app. The Travel Agent extracts the fare details and passes the structured payload to the Expense & Compliance Agent — which validates the amount against corporate policy, logs an audit-ready reimbursement entry, and confirms it all in seconds. Meanwhile, the ops team watches the entire trace flow through Grafana."_

## Overview

In this exercise you will:

1. Build the **Expense & Compliance Agent** — a second Jakarta EE (Java A2A SDK) agent that standardizes receipts into audit-ready expense logs
2. Wire up **cross-agent data handoff** — the Travel Agent extracts receipt details, the Orchestrator routes them to the Expense Agent
3. Add **OpenTelemetry (OTel) tracing** to all agents — Java and Python
4. Visualize **distributed traces** in Grafana across the full Orchestrator → Agent call chain

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

This is another **WildFly 40 + Java A2A SDK** agent — the same pattern you learned in Exercise 1 with the Schedule Advisor.

### Step 1: The ExpenseTool

Open `src/main/java/dev/devconf/expense/ExpenseTool.java`. This tool gives the LLM access to expense management operations:

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
    public String logExpense(String vendor, String amount, String date,
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
            You standardize receipts and session attendance into
            corporate audit-ready expense logs.
            Validate expenses against compliance rules:
            - Meals: max $75/day, Transportation: max $200/trip
            All expenses require: vendor, amount, date, category.
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

# Build the WAR and provision WildFly
mvn package

# Start WildFly with port offset (HTTP on 8082)
./target/wildfly/bin/standalone.sh -Djboss.socket.binding.port-offset=2
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
        "parts": [{"text": "Process this receipt:\nvendor: Airport Express Cabs\namount: $45.00\ndate: 2026-10-07\ncategory: Transportation\ndescription: Taxi from airport to convention center"}]
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

### Update the Orchestrator

Add the Expense Agent to the Orchestrator's discovery list. In `exercises/exercise-5-orchestrator/src/main/resources/application.properties`:

```properties
concierge.agent-urls=http://localhost:8080,http://localhost:8081,http://localhost:9000,http://localhost:8082
```

Restart the Orchestrator, then test the full flow:

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
        "parts": [{"text": "I took a $45 taxi from Airport Express Cabs to the convention center today. Can you log this as an expense?"}]
      }
    },
    "id": "test-handoff"
  }' | jq .
```

The Orchestrator should decompose this and route to the Expense Agent.

---

## Part 3: Enterprise Observability — OpenTelemetry + Grafana

Your agent mesh works. Now you need to **see inside it**.

### Add OpenTelemetry to the Quarkus Orchestrator

Add the `quarkus-opentelemetry` dependency to `exercises/exercise-5-orchestrator/pom.xml`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
```

> You can find this snippet in `exercises/exercise-4-observability/quarkus-otel-pom-additions.xml`.

Add OTel configuration to `application.properties`:

```properties
quarkus.otel.enabled=true
quarkus.otel.exporter.otlp.traces.endpoint=http://localhost:4317
quarkus.otel.service.name=${a2a.agent.name}
quarkus.otel.instrument.rest=true
quarkus.otel.instrument.rest-client=true
```

### Add OpenTelemetry to the Spring Boot Venue Agent

Add these dependencies to `exercises/exercise-2-venue-agent/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
```

Add to `application.properties`:

```properties
management.tracing.enabled=true
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=http://localhost:4317
spring.application.name=Venue & On-Site Operations Agent
```

### Add OpenTelemetry to the Python Travel Agent

```bash
cd exercises/exercise-3-travel-agent
pip install opentelemetry-sdk opentelemetry-exporter-otlp-proto-grpc
```

Add the initialization code from `exercises/exercise-4-observability/python_otel_setup.py` to `travel_agent.py` before the `main()` call:

```python
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

def setup_otel():
    resource = Resource.create({"service.name": "Travel & Logistics Agent (Python)"})
    provider = TracerProvider(resource=resource)
    exporter = OTLPSpanExporter(endpoint="http://localhost:4317", insecure=True)
    provider.add_span_processor(BatchSpanProcessor(exporter))
    trace.set_tracer_provider(provider)
    return trace.get_tracer("travel-logistics-agent")

tracer = setup_otel()
```

Then wrap the `execute` method with a custom span:

```python
async def execute(self, context, event_queue):
    with tracer.start_as_current_span("travel-agent.execute") as span:
        # ... existing logic ...
        span.set_attribute("a2a.user.query", user_message)
```

### Custom Spans for LLM Calls

See `exercises/exercise-4-observability/TracedAgentExecutor.java` for a drop-in replacement that adds fine-grained spans around LLM calls:

```java
Span llmSpan = tracer.spanBuilder("langchain4j.chat").startSpan();
try (Scope llmScope = llmSpan.makeCurrent()) {
    response = service.chat(userText);
    llmSpan.setAttribute("a2a.response.length", response.length());
} finally {
    llmSpan.end();
}
```

Restart all agents after adding OTel configuration.

---

## Part 4: Visualize in Grafana

Open the Grafana UI: **http://localhost:3000** (login: admin/admin), then navigate to **Explore → Tempo**.

Send a complex query to the Orchestrator:

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
        "parts": [{"text": "My flight was delayed so I missed the morning shuttle. I am interested in agentic AI and Java agents. What talks should I catch today, how do I get to the venue quickly, and can you log my $45 taxi receipt from Airport Express Cabs?"}]
      }
    },
    "id": "trace-test"
  }'
```

In Grafana, select **"DevSphere Orchestrator"** and click **Find Traces**. You should see:

```
DevSphere Orchestrator
├── POST / (incoming request)
│   ├── orchestrator.execute
│   │   ├── langchain4j.chat (QueryDecomposer)     ← LLM call ~2-5s
│   │   ├── POST http://localhost:8080/          ← Schedule Advisor
│   │   │   └── schedule-advisor.execute
│   │   │       └── langchain4j.chat                ← LLM call ~2-5s
│   │   ├── POST http://localhost:9000/             ← Travel Agent
│   │   │   └── travel-agent.execute                ← Python span
│   │   ├── POST http://localhost:8082/          ← Expense Agent
│   │   │   └── expense-agent.execute
│   │   │       └── langchain4j.chat                ← LLM call ~2-5s
│   │   └── langchain4j.chat (ResponseAggregator)   ← LLM call ~2-5s
```

This gives you:
- **Total latency**: How long the full request took
- **Per-agent latency**: How long each specialist agent took
- **LLM call duration**: How much time was spent in the OpenAI Responses API
- **Network overhead**: The gap between spans shows serialization/network time

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

See `exercises/exercise-4-observability/StatefulTaskStore.java` for a conceptual implementation using `ConcurrentHashMap`. In production, replace with Redis or PostgreSQL.

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
- [x] **OpenTelemetry tracing** across all agents (Java + Python)
- [x] **Grafana visualization** showing the full Orchestrator → Agent call chain

---

## The Complete DevSphere System

```
┌──────────────────────────────────────────────────────────────────┐
│                    DevSphere Concierge System                    │
│                                                                  │
│  ┌──────────────┐  ┌─────────────┐  ┌───────────────────────┐  │
│  │ Orchestrator │  │  Schedule   │  │  Travel & Logistics   │  │
│  │ & Concierge  │  │  & Content  │  │  Agent (Python)       │  │
│  │ :8090 (Qkus) │──│  Advisor    │  │  :9000                │  │
│  │              │  │  :8080(A2A) │  │                       │  │
│  │              │  ├─────────────┤  ├───────────────────────┤  │
│  │              │  │   Venue &   │  │  Expense &            │  │
│  │              │──│  On-Site Ops│  │  Compliance Agent     │  │
│  │              │  │  :8081(Boot)│  │  :8082 (A2A)         │  │
│  └──────────────┘  └─────────────┘  └───────────────────────┘  │
│       │                    │                    │                │
│       └────────────────────┼────────────────────┘                │
│                            │                                     │
│                    ┌───────▼───────┐                             │
│                    │ Grafana/LGTM  │  ← All traces visible here  │
│                    │    :3000      │                             │
│                    └───────────────┘                             │
└──────────────────────────────────────────────────────────────────┘
```

## What You Built Today

In two hours, you built a **production-grade A2A agent ecosystem**:

1. **Exercise 1** — Your first A2A agent: the Schedule & Content Advisor (Jakarta EE / Java A2A SDK), teaching AgentCard, AgentExecutor, and LLM tool calling
2. **Exercise 2** — The Venue & On-Site Operations Agent (Spring Boot + LangChain4j), proving A2A is runtime-agnostic
3. **Exercise 3** — The Travel & Logistics Agent (Python), proving A2A is language-independent
4. **Exercise 4** — The Expense & Compliance Agent (Java A2A SDK) for cross-agent data handoff, plus OpenTelemetry observability
5. **Exercise 5** — The Orchestrator & Concierge (Quarkus Native), dynamically discovering, decomposing, dispatching, and aggregating across the mesh

### Going Further

- **Security**: Add OAuth2/OIDC authentication to your agents using Keycloak
- **Streaming**: Enable SSE streaming for real-time responses (`StreamMessage` instead of `SendMessage`)
- **Push Notifications**: Register webhooks so agents can proactively notify each other
- **Service Registry**: Replace hardcoded URLs with Consul or Kubernetes service discovery
- **MCP Integration**: Give agents access to external tools via MCP

### Resources

- [A2A Protocol Specification](https://google.github.io/A2A/)
- [A2A Java SDK](https://github.com/a2aproject/a2a-java-sdk)
- [A2A Jakarta EE SDK (Java A2A SDK)](https://github.com/wildfly-extras/a2a-jakarta)
- [A2A Python SDK](https://github.com/a2aproject/a2a-python-sdk)
- [A2A Samples](https://github.com/a2aproject/a2a-samples)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)

---

> **Troubleshooting:**
>
> - **Authentication or billing error**: Check `OPENAI_API_KEY` and confirm API billing is enabled. ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.
> - **Empty responses**: The first LLM call can be slow. Increase the OpenAI HTTP read timeout in `ExpenseServiceProducer` if needed.
> - **Port 8082 conflict**: Make sure you're starting WildFly with `-Djboss.socket.binding.port-offset=2`.
> - **No traces in Grafana**: Verify the LGTM stack is running on port 3000 (Grafana) and OTLP collector is on port 4317. Check that all agents have OTel configured correctly.
