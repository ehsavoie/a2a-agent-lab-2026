# Exercise 4 — Expense & Compliance Agent (20 min)

> *"Maya uploads her taxi receipt photo. The Travel Agent extracts the fare details and the Expense & Compliance Agent processes it into an audit-ready reimbursement entry. Meanwhile, the ops team watches the full trace in Grafana."*

Build a **Jakarta EE enterprise agent on WildFly 41** that standardizes receipts into corporate audit-ready expense logs with compliance validation. The exercise also includes a JAX-RS web client that calls the agent via the A2A Java SDK.

This exercise has two sub-projects:
- **`expense-agent/`** — the A2A agent (LangChain4j + compliance validation)
- **`expense-client/`** — a JAX-RS web app — A2A client of the agent

## Context

| | |
|---|---|
| **Runtime** | WildFly 41 (Jakarta EE) |
| **Transport** | JSON-RPC (+ REST + gRPC optional) |
| **Port** | 8082 (agent), 8083 (client) |
| **LLM** | OpenAI Responses API via `OpenAiResponsesChatModel` |

### Compliance Rules

| Category | Limit |
|---|---|
| Meals | €75 per day |
| Transportation | €200 per day |

## What You Will Build

### Part 1 — Expense Agent (`expense-agent/`)

Two files need implementation:

1. **`ExpenseAgentCardProducer.java`** — Implements `agentCard()` reading the WildFly port-offset at runtime
2. **`ExpenseAgentExecutorProducer.java`** — Implements `agentExecutor()` wiring the message handler

The following are already provided and can be studied:
- **`ExpenseTool.java`** — `@Tool` methods: `logExpense()`, `processReceipt()`, `checkComplianceStatus()`
- **`ExpenseServiceProducer.java`** — wires `OpenAiResponsesChatModel` + `AiServices.builder()`

### Part 2 — Expense Client (`expense-client/`)

One method needs implementation:
- **`ExpenseClientResource.java`** — implement the `askAgent(String query)` private method (3 steps)

## Step 1 — Implement the AgentCard Producer

Open `expense-agent/src/.../ExpenseAgentCardProducer.java`. The `isRest()` and `isGrpcEnabled()` helpers and the port constants are already provided.

1. Read the port offset and compute the base URL:
   ```java
   int portOffset = ConfigProvider.getConfig()
       .getOptionalValue("jboss.socket.binding.port-offset", Integer.class).orElse(0);
   String jsonRpcUrl = "http://localhost:" + (BASE_HTTP_PORT + portOffset);
   ```

2. Build the `interfaces` list conditionally:
   - Always add `AgentInterface(TransportProtocol.JSONRPC.asString(), jsonRpcUrl)`
   - If `isRest()`: add `AgentInterface(TransportProtocol.HTTP_JSON.asString(), jsonRpcUrl)`
   - If `isGrpcEnabled()`: add `AgentInterface(TransportProtocol.GRPC.asString(), "localhost:" + (BASE_GRPC_PORT + portOffset))`

3. Call `AgentCard.builder()` with name, description, version, `supportedInterfaces(interfaces)`, `capabilities` with `streaming(true)`, and the three skills below.

Skills to use:
- `expense-logging` — Log and validate expense entries against corporate compliance rules
- `receipt-processing` — Process receipt data from other agents into audit-ready expense entries
- `compliance-report` — Generate compliance status reports and flag policy violations

## Step 2 — Implement the AgentExecutor Producer

Open `ExpenseAgentExecutorProducer.java`. Return an anonymous `AgentExecutor` with:

**`execute(RequestContext, AgentEmitter)`:**
1. Extract user text with `extractText(context.getMessage())`
2. If this is a new task (`context.getTask() == null`), call `emitter.submit()` first
3. Call `emitter.startWork()`
4. Call `expenseServiceProducer.getExpenseService().chat(userText)`
5. On success: `emitter.addArtifact(...)` then `emitter.complete()`
6. On exception: `emitter.addArtifact(...)` with the error message then `emitter.fail()`

**`cancel(RequestContext, AgentEmitter)`:** throw `new TaskNotCancelableError()`

> The extra `emitter.submit()` call on new tasks transitions the task to `SUBMITTED` before starting work, letting the client know the task was accepted before LLM processing begins.

## Step 3 — Build & Run the Expense Agent

```bash
export OPENAI_API_KEY=your-api-key-here

cd exercises/exercise-4-expense-agent/expense-agent
mvn package
./target/wildfly/bin/standalone.sh -Djboss.socket.binding.port-offset=2
```

Agent starts on **port 8082**.

## Verify the Agent

```bash
# Check the AgentCard
curl -s http://localhost:8082/.well-known/agent-card.json \
  -H "A2A-Version: 1.0" | jq .name
# → "Expense & Compliance Agent"

# Log a taxi expense via JSON-RPC
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
        "parts": [{"text": "Log a taxi expense: vendor Bolt Belgium, amount 45 EUR, date 2026-10-07, ground transportation"}]
      }
    },
    "id": "test-1"
  }' | jq .result.task.artifacts[0].parts[0].text

# Log a taxi expense via REST
curl -s -X POST http://localhost:8082/message:send \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "message": {
      "messageId": "msg-2",
      "role": "ROLE_USER",
      "parts": [{"text": "Log a taxi expense: vendor Bolt Belgium, amount 45 EUR, date 2026-10-07, ground transportation"}],
      "metadata": {}
    }
  }' | jq '.task.artifacts[0].parts[0].text'
```

## Step 4 — Implement the Expense Client

Open `expense-client/.../ExpenseClientResource.java`. The three JAX-RS endpoints (`submitExpense`, `getSummary`, `getCompliance`) and the `extractText()` helper are provided. Implement the `askAgent(String query)` private method:

**Step 1 — Fetch the AgentCard:**
```java
AgentCard agentCard = A2ACardResolver.builder()
    .baseUrl(AGENT_BASE_URL).build().getAgentCard();
```

**Step 2 — Build the A2A client** (use try-with-resources, `Client` is `AutoCloseable`):
```java
try (Client client = Client.builder(agentCard)
        .withTransport(RestTransport.class, new RestTransportConfigBuilder())
        .build()) { ... }
```

**Step 3 — Build the message, send it, and collect the response:**
1. Build: `Message.builder().role(Message.Role.ROLE_USER).parts(List.of(new TextPart(query))).build()`
2. Create: `CompletableFuture<String> result = new CompletableFuture<>()`
3. Call `client.sendMessage(message, listeners, errorHandler, null)` where the listener:
   - On `TASK_STATE_FAILED` → `result.completeExceptionally(...)`
   - On `TASK_STATE_COMPLETED` → `result.complete(extractText(update.getTask().artifacts()))`
   - Error handler: `error -> result.completeExceptionally(...)`
4. Return `Response.ok(new ExpenseResponse(result.get(30, TimeUnit.SECONDS))).build()`

## Step 5 — Build & Run the Expense Client

```bash
cd exercises/exercise-4-expense-agent/expense-client
mvn package
./target/wildfly/bin/standalone.sh -Djboss.socket.binding.port-offset=3
```

Client starts on **port 8083**. Open [http://localhost:8083](http://localhost:8083) for the browser UI.

## Verify the Client

```bash
# Log a structured expense
curl -s -X POST http://localhost:8083/api/expense \
  -H "Content-Type: application/json" \
  -d '{
    "vendor": "Bolt Belgium",
    "amount": "45",
    "currency": "EUR",
    "date": "2026-10-07",
    "category": "Transportation",
    "description": "Taxi from Brussels Airport to Kinepolis Antwerp"
  }' | jq .response

# Get expense summary
curl -s http://localhost:8083/api/expense/summary | jq .response

# Get compliance status
curl -s http://localhost:8083/api/expense/compliance | jq .response
```

## Cross-Agent Data Handoff

The key integration point in Maya's scenario: the Travel Agent's `receipt-extraction` skill outputs structured data that the Expense Agent's `receipt-processing` skill consumes:

```
Travel Agent                            Expense Agent
extract_receipt(query)         →        processReceipt(receiptData)
  {"vendor": "Bolt Belgium",               Validate: €45 < €200 limit ✓
   "amount": "45.00",                      Category: ground_transportation ✓
   "currency": "EUR",                      Log: EXP-001 created
   "date": "2026-10-07",                   Return: audit-ready entry
   "category": "ground_transportation"}
```

## Checkpoint

- [ ] Expense Agent running on port 8082 (JSON-RPC + REST)
- [ ] Expense Client running on port 8083 with browser UI at [http://localhost:8083](http://localhost:8083)
- [ ] `POST /api/expense` logs a structured expense via the A2A client
- [ ] `GET /api/expense/summary` and `/compliance` query the agent for reports
- [ ] Receipts validated against compliance limits

## Skills

| Skill ID | Description |
|---|---|
| `expense-logging` | Log and validate expense entries against corporate compliance rules |
| `receipt-processing` | Process receipt data from other agents into audit-ready expense entries |
| `compliance-report` | Generate compliance status reports and flag policy violations |
