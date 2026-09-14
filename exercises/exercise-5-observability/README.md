# Exercise 5: Enterprise Day-2 — Observability & Production Readiness

**Time:** 20 minutes

> _The conference is live. 5,000 attendees are hitting the Concierge. The ops team needs to see what's happening: which agents are being called, how long each takes, where failures occur. Welcome to Day-2._

## What You Build

OpenTelemetry tracing across all agents (Quarkus, Spring Boot, Python), visualized in **Jaeger**. You'll also explore patterns for distributed state management and load balancing of stateful LLM conversations.

## What's in This Directory

This directory contains configuration snippets and code to **add on top of** the agents built in Exercises 1-4.

| File | Description |
|------|-------------|
| `quarkus-otel-pom-additions.xml` | Maven dependency to add to Quarkus agents (Ex 1 + Ex 4) |
| `quarkus-otel-properties.properties` | OTel exporter config for Quarkus `application.properties` |
| `spring-otel-pom-additions.xml` | Maven dependencies for Spring Boot agent (Ex 2a) |
| `spring-otel-properties.properties` | OTel exporter config for Spring Boot `application.properties` |
| `python-otel-requirements.txt` | pip dependencies for the Python agent (Ex 3) |
| `python_otel_setup.py` | OTel initialization code to add to `travel_agent.py` |
| `TracedAgentExecutor.java` | Example AgentExecutor with custom OTel spans around LLM calls |
| `StatefulTaskStore.java` | Conceptual distributed state management using A2A `contextId` |
| `nginx-sticky-sessions.conf` | Load balancer config for sticky session routing |

## Prerequisites

- All agents from Exercises 1-4 running
- **Jaeger** running on port **16686** (started via `podman-compose up -d`)

## How to Apply

### Quarkus Agents (Exercise 1 + Exercise 4)

1. Add the dependency from `quarkus-otel-pom-additions.xml` to the agent's `pom.xml`
2. Append the properties from `quarkus-otel-properties.properties` to `application.properties`
3. Restart with `mvn quarkus:dev`

### Spring Boot Agent (Exercise 2a)

1. Add the dependencies from `spring-otel-pom-additions.xml` to `pom.xml`
2. Append the properties from `spring-otel-properties.properties` to `application.properties`
3. Restart

### Python Agent (Exercise 3)

1. Install: `pip install opentelemetry-sdk opentelemetry-exporter-otlp`
2. Add the code from `python_otel_setup.py` to `travel_agent.py` (before `main()`)
3. Restart

### Custom Spans (Optional)

Replace the anonymous `AgentExecutor` in `SessionAgentExecutorProducer.java` with `TracedAgentExecutor.java` to get fine-grained spans around LLM calls.

## How to Verify

1. Open Jaeger UI at **http://localhost:16686**
2. Send a complex query to the Concierge on port 8090
3. In Jaeger, select service **"DevConf Concierge"** and click **Find Traces**
4. Open the trace — you should see the full call chain:
   - `Concierge → Session Agent` (query decomposition + dispatch)
   - `Concierge → Travel Tips Agent` (parallel dispatch)
   - LLM call durations within each agent

## Full Instructions

See [../../docs/exercise-5.md](../../docs/exercise-5.md) for the complete step-by-step guide, including the distributed state and load balancing discussion.
