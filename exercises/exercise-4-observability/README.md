# Exercise 4: Enterprise Day-2 — Observability & Production Readiness

**Time:** 20 minutes

> _The conference is live. 5,000 attendees are hitting the Orchestrator. The ops team needs to see what's happening: which agents are being called, how long each takes, where failures occur. Welcome to Day-2._

## What You Build

OpenTelemetry tracing across all agents (Quarkus, Spring Boot, WildFly/Java A2A SDK, Python), visualized in **Grafana** using the LGTM stack (Loki, Grafana, Tempo, Mimir). You'll also explore patterns for distributed state management and load balancing of stateful LLM conversations.

## What's in This Directory

This directory contains configuration snippets and code to **add on top of** the agents built in Exercises 1-3.

| File | Description |
|------|-------------|
| `quarkus-otel-pom-additions.xml` | Maven dependency to add to the Orchestrator (Ex 5) |
| `quarkus-otel-properties.properties` | OTel exporter config for Quarkus `application.properties` |
| `spring-otel-pom-additions.xml` | Maven dependencies for the Venue Agent (Ex 2) |
| `spring-otel-properties.properties` | OTel exporter config for Spring Boot `application.properties` |
| `python-otel-requirements.txt` | pip dependencies for the Travel Agent (Ex 3) |
| `python_otel_setup.py` | OTel initialization code to add to `travel_agent.py` |
| `TracedAgentExecutor.java` | Example AgentExecutor with custom OTel spans around LLM calls |
| `StatefulTaskStore.java` | Conceptual distributed state management using A2A `contextId` |
| `nginx-sticky-sessions.conf` | Load balancer config for sticky session routing |

## Target Agents

| Agent | Framework | Port | Config to apply |
|-------|-----------|------|-----------------|
| Schedule & Content Advisor | Quarkus | 8080 | `quarkus-otel-*` files |
| Venue & On-Site Operations Agent | Spring Boot | 8081 | `spring-otel-*` files |
| Travel & Logistics Agent | Python | 9000 | `python_otel_setup.py` |
| Orchestrator & Concierge | Quarkus | 8090 | `quarkus-otel-*` files |
| Expense & Compliance Agent | WildFly (Java A2A SDK) | 8082 | Java A2A SDK OTel (manual spans) |

## Prerequisites

- All agents from Exercises 1-5 running
- **LGTM stack** running (started via `podman-compose up -d`) — Grafana on port **3000**

## How to Apply

### Quarkus Agents (Schedule Advisor — Exercise 1; Orchestrator — Exercise 5)

1. Add the dependency from `quarkus-otel-pom-additions.xml` to the agent's `pom.xml`
2. Append the properties from `quarkus-otel-properties.properties` to `application.properties`, omitting
   `quarkus.otel.instrument.rest-client`, which Quarkus 3.39.4 does not recognize.
3. Restart with `mvn quarkus:dev`

### Spring Boot Agent (Venue Agent — Exercise 2)

1. Add the dependencies from `spring-otel-pom-additions.xml` to `pom.xml`
2. Append the properties from `spring-otel-properties.properties` to `application.properties`
3. Restart

### Python Agent (Travel Agent — Exercise 3)

1. Install: `pip install opentelemetry-sdk opentelemetry-exporter-otlp-proto-grpc`
2. Add the code from `python_otel_setup.py` to `travel_agent.py` (before `main()`)
3. Restart

### WildFly Java A2A SDK Agent (Expense Agent — Exercise 4)

Java A2A SDK agents use manual OTel spans. See `TracedAgentExecutor.java` for the pattern — wrap the `execute()` method in a custom span and inject a `Tracer` from the OTel API.

### Custom Spans (Optional)

Replace the anonymous `AgentExecutor` in an agent's executor producer with an adapted `TracedAgentExecutor.java` to get fine-grained spans around LLM calls. The checked-in example is written for `SessionService`; adapt the service type, imports, package, and producer wiring for other agents.

## How to Verify

1. Open Grafana at **http://localhost:3000** (login: admin/admin)
2. Send a complex query to the Orchestrator on port 8090
3. Navigate to **Explore → Tempo** and search for traces from **"DevSphere Orchestrator"**
4. Open the trace — you should see the full call chain:
   - `Orchestrator → Schedule Advisor` (session query dispatch)
   - `Orchestrator → Venue Agent` (room capacity check)
   - `Orchestrator → Travel Agent` (transit routing)
   - `Orchestrator → Expense Agent` (receipt processing)
   - LLM call durations within each agent

## Companion Project

The companion Expense & Compliance Agent project is in [exercise-4-expense-agent](../exercise-4-expense-agent/README.md).

## Full Instructions

See [../../docs/exercise-4.md](../../docs/exercise-4.md) for the complete step-by-step guide, including the distributed state and load balancing discussion.
