# Exercise 4: Expense & Compliance Agent (WildFly)

The **Expense & Compliance Agent** standardizes receipts into corporate audit-ready expense logs. It validates expenses against corporate compliance rules and can process structured receipt data received from other agents (e.g. the Travel & Logistics Agent).

## Technology

- **Runtime:** WildFly 41 (Jakarta EE)
- **A2A SDK:** `a2a-jakarta-jsonrpc` / `a2a-jakarta-rest`
- **LLM:** LangChain4j + OpenAI GPT-6 Luna via the Responses API at medium reasoning effort
- **Port:** 8082 (WildFly with port offset 2)

## Transports

The agent includes JSON-RPC and REST transports. The `AgentCardProducer` advertises the transports available on the classpath.

## Build & Run

Set an OpenAI API key with API billing enabled before starting the agent. ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.

```bash
export OPENAI_API_KEY=your-api-key-here

# Build the agent and provision WildFly
mvn package

# Refresh the deployed WAR when the provisioned server already exists
cp expense-agent/target/expense-agent-1.0.0-SNAPSHOT.war expense-agent/target/wildfly/standalone/deployments/ROOT.war

# Start WildFly on port 8082 (base port 8080 + offset 2)
./expense-agent/target/wildfly/bin/standalone.sh -Djboss.socket.binding.port-offset=2
```

## Skills

| Skill | Description |
|-------|-------------|
| `expense-logging` | Log and validate expense entries against corporate compliance rules |
| `receipt-processing` | Process receipt data from other agents into audit-ready entries |
| `compliance-report` | Generate compliance status reports and flag policy violations |

## Compliance Rules

| Category | Max Amount |
|----------|-----------|
| Meals | $75/day |
| Transportation | $200/trip |
| Accommodation | $350/night |
| Registration | $500 |
| Supplies | $50 |

## Test

```bash
# Check the AgentCard
curl -s http://localhost:8082/.well-known/agent-card.json | jq .

# Log an expense
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
        "parts": [{"text": "Log my taxi receipt: $35 from Airport Express Taxi on 2026-10-07 for transportation to the venue"}]
      }
    },
    "id": "1"
  }' | jq .
```

## Key Files

| File | Purpose |
|------|---------|
| `ExpenseTool.java` | `@Tool` methods for logging expenses, processing receipts, compliance checks |
| `ExpenseService.java` | AI Service interface for expense operations |
| `ExpenseServiceProducer.java` | CDI producer that builds the AI Service with an OpenAI Responses API chat model |
| `ExpenseAgentCardProducer.java` | Port-offset-aware AgentCard with multi-transport auto-detection |
| `ExpenseAgentExecutorProducer.java` | CDI producer for the AgentExecutor |
| `microprofile-config.properties` | A2A authorization and OpenAI model configuration |

## Full Instructions

See [../../docs/exercise-4.md](../../docs/exercise-4.md) for the complete step-by-step guide.
