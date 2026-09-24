# Solution: Exercise 4 — Expense & Compliance Agent (WildFly Enterprise) + Observability

The complete working **Expense & Compliance Agent** deployed on WildFly 41 with enterprise features (JPA persistence, Kafka replication, multi-transport support), plus configuration snippets for adding **OpenTelemetry** across the agents.

## What's Included

### Expense Agent
The full source lives in `../../exercises/exercise-4-expense-agent/`:

| File | Purpose |
|------|---------|
| `ExpenseTool.java` | `@Tool` methods for logging expenses, processing receipts, compliance checks |
| `ExpenseService.java` | AI Service interface for expense operations |
| `ExpenseServiceProducer.java` | CDI producer that builds the AI Service with an OpenAI Responses API chat model |
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
| `python-otel-requirements.txt` | Travel Agent optional dependencies | Lists the packages needed for OTel tracing |
| `python_otel_setup.py` | Travel Agent `travel_agent.py` | OTel initialization |

## Prerequisites

- For the full-system run, the script starts agents from Exercises 1-3 and the Exercise 5 Orchestrator. They are not needed to run the Expense Agent alone.
- `OPENAI_API_KEY` set to an OpenAI API key with API billing enabled for `gpt-6-luna` through the Responses API at medium reasoning effort. ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.
- Shared PostgreSQL, Kafka, and LGTM services running from the [development compose file](../../exercises/exercise-5-orchestrator/podman-compose.yml) — Grafana on port **3000**, Kafka on **9092**

Start them from the repository root:

```bash
cd exercises/exercise-5-orchestrator
podman-compose up -d
podman exec devconf-kafka /opt/kafka/bin/kafka-topics.sh --create --if-not-exists \
  --topic replicated-events --bootstrap-server localhost:9092 --partitions 1
cd ../../solutions/exercise-4
```

## How to Run the Expense Agent

```bash
export OPENAI_API_KEY=your-api-key-here
export POSTGRESQL_DATABASE=devconf
export POSTGRESQL_USER=devconf
export POSTGRESQL_PASSWORD=devconf
# Full-system demo (starts agents from Exercises 1-5):
./run-full-system.sh
# Or run only the Expense Agent:
cd ../../exercises/exercise-4-expense-agent
mvn package -Pjsonrpc
cp target/expense-agent-1.0.0-SNAPSHOT.war target/wildfly/standalone/deployments/ROOT.war
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
