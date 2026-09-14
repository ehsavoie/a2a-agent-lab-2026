# Solution: Exercise 5 — Full Observability

Add **OpenTelemetry tracing** to every agent in the mesh and visualize the full distributed call chain in **Jaeger**.

## What to Apply

The `../../exercises/exercise-5-observability/` directory contains config snippets and code to layer on top of the agents from Exercises 1-4:

| File | Target | What It Does |
|------|--------|-------------|
| `quarkus-otel-pom-additions.xml` | Exercise 1 + 4 `pom.xml` | Adds `quarkus-opentelemetry` dependency |
| `quarkus-otel-properties.properties` | Exercise 1 + 4 `application.properties` | Configures OTLP exporter → Jaeger |
| `spring-otel-pom-additions.xml` | Exercise 2a `pom.xml` | Adds actuator + micrometer OTel bridge |
| `spring-otel-properties.properties` | Exercise 2a `application.properties` | Configures trace export → Jaeger |
| `python-otel-requirements.txt` | Exercise 3 `pyproject.toml` | OTel SDK + OTLP exporter |
| `python_otel_setup.py` | Exercise 3 `travel_agent.py` | OTel initialization (add before `main()`) |
| `TracedAgentExecutor.java` | Exercise 1 (optional) | Custom spans around LLM calls |
| `StatefulTaskStore.java` | Discussion | Distributed state via A2A `contextId` |
| `nginx-sticky-sessions.conf` | Discussion | Sticky session routing by `contextId` |

## Prerequisites

- All agents from Exercises 1-4 running
- **Jaeger** running (started by `podman-compose up -d` from the project root)

## How to Apply

### Quarkus Agents (Session Agent + Concierge)

1. Copy the dependency from `quarkus-otel-pom-additions.xml` into the `<dependencies>` section of `exercise-1-session-agent/pom.xml` and `exercise-4-concierge/pom.xml`
2. Append the contents of `quarkus-otel-properties.properties` to each agent's `application.properties`
3. Restart each agent

### Spring Boot Agent

1. Copy the dependencies from `spring-otel-pom-additions.xml` into `exercise-2a-spring-boot/pom.xml`
2. Append `spring-otel-properties.properties` to `application.properties`
3. Restart

### Python Agent

1. `pip install opentelemetry-sdk opentelemetry-exporter-otlp`
2. Add the code from `python_otel_setup.py` to `travel_agent.py` (before `main()`)
3. Restart

## How to Verify

1. Open **Jaeger UI**: http://localhost:16686
2. Send a complex query to the Concierge (port 8090)
3. In Jaeger, select service **"DevConf Concierge"** → **Find Traces**
4. Open the trace to see the full call chain:
   - Concierge receives the query
   - QueryDecomposer LLM call
   - Parallel dispatch to Session Agent + Travel Agent
   - Each agent's LLM call
   - ResponseAggregator LLM call
   - Final response

## Discussion Topics

### Distributed State Management
See `StatefulTaskStore.java` — uses A2A's `contextId` to persist conversation state. In production, replace the in-memory map with Redis or a database.

### Load Balancing for Stateful Conversations
See `nginx-sticky-sessions.conf` — routes all messages with the same `contextId` to the same backend instance, enabling multi-turn conversations across replicas.
