# Solution: Exercise 4 — Expense & Compliance Agent (WildFly Enterprise) + Observability

The complete working **Expense & Compliance Agent** deployed on WildFly 41 with enterprise features (JPA persistence, Kafka replication, multi-transport support) plus **OpenTelemetry** integration across all agents.

## What's Included

### Expense Agent
The full source lives in `../../exercises/exercise-4-expense-agent/`:

| File | Purpose |
|------|---------|
| `ExpenseTool.java` | `@Tool` methods for logging expenses, processing receipts, compliance checks |
| `ExpenseService.java` | AI Service interface for expense operations |
| `ExpenseServiceProducer.java` | CDI producer that builds the AI Service with GoogleAiGeminiChatModel |
| `ExpenseAgentCardProducer.java` | Port-offset-aware AgentCard with multi-transport auto-detection |
| `ExpenseAgentExecutorProducer.java` | CDI producer for the AgentExecutor |
| `persistence.xml` | JPA persistence unit for PostgreSQL-backed task/notification stores |
| `microprofile-config.properties` | Kafka SmallRye Reactive Messaging configuration |

### Enterprise Features

- **JPA TaskStore** — Tasks persisted in PostgreSQL instead of in-memory
- **JPA PushNotificationConfigStore** — Notification configs persisted in PostgreSQL
- **Replicated Queue Manager** — Multi-node task queue via Kafka broadcast events
- **Multi-Transport** — JSON-RPC, REST, or gRPC via Maven profiles (`-Pjsonrpc`, `-Prest`, `-Pgrpc`)

### Observability
Configuration snippets in `../../exercises/exercise-4-observability/`:

| File | Target | What It Does |
|------|--------|-------------|
| `quarkus-otel-pom-additions.xml` | Orchestrator `pom.xml` | Adds `quarkus-opentelemetry` dependency |
| `quarkus-otel-properties.properties` | Orchestrator `application.properties` | Configures OTLP exporter → LGTM |
| `spring-otel-pom-additions.xml` | Venue Agent `pom.xml` | Adds actuator + micrometer OTel bridge |
| `spring-otel-properties.properties` | Venue Agent `application.properties` | Configures trace export → LGTM |
| `python_otel_setup.py` | Travel Agent `travel_agent.py` | OTel initialization |

## Prerequisites

- All agents from Exercises 1-3 running
- **LGTM stack** running (started by `podman-compose up -d`) — Grafana on port **3000**

## How to Run the Expense Agent

```bash
./run-full-system.sh
# Or manually:
cd ../../exercises/exercise-4-expense-agent
mvn package -Pjsonrpc
./target/wildfly/bin/standalone.sh -Djboss.socket.binding.port-offset=2
```

The Expense Agent starts on **port 8082** (WildFly with port offset 2).

## How to Verify

```bash
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
  }' | python3 -m json.tool

# Check Grafana (Explore → Tempo for traces)
open http://localhost:3000
```
