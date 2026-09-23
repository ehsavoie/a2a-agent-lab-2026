# Exercise 4: Expense & Compliance Agent (WildFly Enterprise)

The **Expense & Compliance Agent** standardizes receipts and session attendance into corporate audit-ready expense logs. It validates expenses against corporate compliance rules and can process structured receipt data received from other agents (e.g. the Travel & Logistics Agent).

## Technology

- **Runtime:** WildFly 41 (Jakarta EE) with enterprise features
- **A2A SDK:** `a2a-jakarta-jsonrpc` / `a2a-jakarta-rest` / `a2a-jakarta-grpc` (via Maven profiles)
- **LLM:** LangChain4j + Google AI Gemini
- **Persistence:** JPA-backed TaskStore + PushNotificationConfigStore (PostgreSQL)
- **Replication:** Kafka replicated queue manager for multi-node deployment
- **Port:** 8082 (WildFly with port offset 2)

## Enterprise Features

This exercise uses the professional enterprise setup from the A2A Jakarta EE SDK:

| Feature | Dependency | What It Does |
|---------|-----------|--------------|
| JPA TaskStore | `a2a-java-extras-task-store-database-jpa` | Persists A2A tasks in PostgreSQL (replaces in-memory) |
| JPA PushNotificationConfigStore | `a2a-java-extras-push-notification-config-store-database-jpa` | Persists push notification configs in PostgreSQL |
| Replicated Queue Manager | `a2a-java-queue-manager-replicated-core` | Enables multi-node task queue via Kafka |
| Kafka Replication | `a2a-java-queue-manager-replication-mp-reactive` | SmallRye Reactive Messaging (Kafka) for event broadcast |

## Transport Profiles

The transport protocol is selected via Maven profiles. Each profile provisions a WildFly server with the required Galleon layers:

```bash
# JSON-RPC transport (default for A2A)
mvn package -Pjsonrpc

# REST transport (JSON-RPC + HTTP/JSON)
mvn package -Prest

# gRPC transport (JSON-RPC + gRPC)
mvn package -Pgrpc
```

The `AgentCardProducer` auto-detects which transports are on the classpath and advertises them in the AgentCard.

## Build & Run

```bash
# Build with JSON-RPC transport
mvn package -Pjsonrpc

# Start WildFly on port 8082 (base port 8080 + offset 2)
./target/wildfly/bin/standalone.sh -Djboss.socket.binding.port-offset=2
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
| `ExpenseServiceProducer.java` | CDI producer that builds the AI Service with GoogleAiGeminiChatModel |
| `ExpenseAgentCardProducer.java` | Port-offset-aware AgentCard with multi-transport auto-detection |
| `ExpenseAgentExecutorProducer.java` | CDI producer for the AgentExecutor |
| `persistence.xml` | JPA persistence unit for JpaTask + JpaPushNotificationConfig |
| `microprofile-config.properties` | Kafka/SmallRye Reactive Messaging configuration |

## Full Instructions

See [../../docs/exercise-4.md](../../docs/exercise-4.md) for the complete step-by-step guide.
