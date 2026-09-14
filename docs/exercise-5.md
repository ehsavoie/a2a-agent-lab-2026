# Exercise 5: Enterprise Day-2 — Observability & Production Readiness (20 min)

> _"The conference is live. 5,000 attendees are hitting the Concierge. The ops team needs to see what's happening: which agents are being called, how long each takes, where failures occur. Welcome to Day-2."_

## Overview

Your agent mesh works. Now you need to **see inside it**. In this exercise you will:

1. Add **OpenTelemetry (OTel) tracing** to all agents — Java and Python
2. Visualize **distributed traces** in Jaeger across the full Concierge → Agent call chain
3. Add **custom spans** around LLM calls for fine-grained performance insight
4. Discuss **distributed state management** and **load balancing** patterns for production A2A deployments

---

## Step 1: Add OpenTelemetry to the Quarkus Agents

Add the `quarkus-opentelemetry` dependency to both `exercises/exercise-1-session-agent/pom.xml` and `exercises/exercise-4-concierge/pom.xml`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
```

> You can find this snippet in `exercises/exercise-5-observability/quarkus-otel-pom-additions.xml`.

Add the OTel configuration to both projects' `application.properties`:

```properties
# OpenTelemetry Configuration
quarkus.otel.enabled=true
quarkus.otel.exporter.otlp.traces.endpoint=http://localhost:4317
quarkus.otel.service.name=${a2a.agent.name}

# Trace all HTTP requests (A2A endpoints)
quarkus.otel.instrument.rest=true
quarkus.otel.instrument.rest-client=true
```

> You can find this in `exercises/exercise-5-observability/quarkus-otel-properties.properties`.

Quarkus auto-instruments HTTP requests, so every incoming A2A `message/send` call and every outgoing A2A client call from the Concierge will automatically generate spans.

Restart both Quarkus agents for the changes to take effect.

---

## Step 2: Add OpenTelemetry to the Spring Boot Agent

Add these dependencies to `exercises/exercise-2a-spring-boot/pom.xml`:

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

> You can find this in `exercises/exercise-5-observability/spring-otel-pom-additions.xml`.

Add the configuration to `application.properties`:

```properties
management.tracing.enabled=true
management.tracing.sampling.probability=1.0
management.otlp.tracing.endpoint=http://localhost:4317
spring.application.name=Session Agent (Spring Boot)
```

> You can find this in `exercises/exercise-5-observability/spring-otel-properties.properties`.

Restart the Spring Boot agent.

---

## Step 3: Add OpenTelemetry to the Python Agent

Install the OTel packages:

```bash
cd exercises/exercise-3-travel-agent-python
pip install opentelemetry-sdk opentelemetry-exporter-otlp-proto-grpc
```

Add the OTel initialization to the top of `travel_agent.py`, **before** the `main()` call. You can copy from `exercises/exercise-5-observability/python_otel_setup.py`:

```python
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor


def setup_otel():
    resource = Resource.create({"service.name": "Travel Tips Agent (Python)"})
    provider = TracerProvider(resource=resource)
    exporter = OTLPSpanExporter(endpoint="http://localhost:4317", insecure=True)
    provider.add_span_processor(BatchSpanProcessor(exporter))
    trace.set_tracer_provider(provider)
    return trace.get_tracer("travel-tips-agent")


tracer = setup_otel()
```

Then wrap the `execute` method in the `TravelAgentExecution` class with a custom span:

```python
async def execute(self, context: RequestContext, event_queue) -> None:
    with tracer.start_as_current_span("travel-agent.execute") as span:
        user_message = ""
        if context.message and context.message.parts:
            for part in context.message.parts:
                if isinstance(part.root, TextPart):
                    user_message = part.root.text
                    break

        span.set_attribute("a2a.user.query", user_message)
        response_text = answer_query(user_message)
        span.set_attribute("a2a.response.length", len(response_text))

        await event_queue.enqueue_event(
            context.build_success_response(
                parts=[Part(root=TextPart(text=response_text))]
            )
        )
```

Restart the Python agent.

---

## Step 4: Custom Spans for LLM Calls

The automatic HTTP instrumentation shows you the A2A request flow, but you also want to see **how long the LLM takes** inside each agent. This is where custom spans come in.

Look at `exercises/exercise-5-observability/TracedAgentExecutor.java`. This is a drop-in replacement for the anonymous `AgentExecutor` in `SessionAgentExecutorProducer`:

```java
public class TracedAgentExecutor implements AgentExecutor {

    @Override
    public void execute(RequestContext context, AgentEmitter emitter) {
        Span span = tracer.spanBuilder("session-agent.execute")
            .setAttribute("a2a.task.id", context.getTask().id())
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            String userText = extractText(context.getMessage());

            // LLM call gets its own span
            Span llmSpan = tracer.spanBuilder("langchain4j.chat").startSpan();
            String response;
            try (Scope llmScope = llmSpan.makeCurrent()) {
                response = sessionService.chat(userText);
                llmSpan.setAttribute("a2a.response.length", response.length());
            } finally {
                llmSpan.end();
            }

            emitter.addArtifact(List.of(new TextPart(response)));
            emitter.complete();
        } finally {
            span.end();
        }
    }
}
```

To use it, update `SessionAgentExecutorProducer` to inject the OTel `Tracer` and return a `TracedAgentExecutor` instead.

---

## Step 5: Visualize in Jaeger

Open the Jaeger UI in your browser:

```
http://localhost:16686
```

Now send a complex query to the Concierge:

```bash
curl -s -X POST http://localhost:8090/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "message/send",
    "params": {
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "I am arriving Thursday and love AI talks. What should I attend and where should I eat?"}]
      }
    },
    "id": "trace-test"
  }'
```

In Jaeger:

1. Select the **"DevConf Concierge"** service from the dropdown
2. Click **Find Traces**
3. Click on the most recent trace

You should see a **waterfall view** showing:

```
DevConf Concierge
├── POST /a2a (incoming request)
│   ├── concierge.execute
│   │   ├── langchain4j.chat (QueryDecomposer)     ← LLM call ~2-5s
│   │   ├── POST http://localhost:8080/a2a          ← Session Agent dispatch
│   │   │   └── session-agent.execute
│   │   │       └── langchain4j.chat                ← LLM call ~2-5s
│   │   ├── POST http://localhost:9000/             ← Travel Agent dispatch
│   │   │   └── travel-agent.execute                ← Python span
│   │   └── langchain4j.chat (ResponseAggregator)   ← LLM call ~2-5s
```

This gives you:
- **Total latency**: How long the full request took
- **Per-agent latency**: How long each specialist agent took
- **LLM call duration**: How much time was spent in Ollama
- **Network overhead**: The gap between spans shows serialization/network time

---

## Step 6: Distributed State (Discussion)

### The Problem

Your Concierge handles multi-turn conversations. An attendee might ask:

1. "What AI sessions are on Thursday?" → Concierge routes to Session Agent
2. "Tell me more about the second one" → Concierge needs to remember which sessions were listed

If the Concierge crashes or gets restarted between messages, the conversation context is lost.

### The A2A Solution: `contextId`

A2A Tasks include a `contextId` field that groups related messages into a conversation. This is your key for state persistence:

```java
// From the A2A spec: tasks in the same conversation share a contextId
String contextId = context.getTask().contextId();
```

Look at `exercises/exercise-5-observability/StatefulTaskStore.java` for a conceptual implementation:

```java
public class StatefulTaskStore {

    private final Map<String, ConversationState> store = new ConcurrentHashMap<>();

    public void saveState(String contextId, ConversationState state) {
        store.put(contextId, state);
    }

    public ConversationState loadState(String contextId) {
        return store.get(contextId);
    }

    public record ConversationState(
        String contextId,
        String lastQuery,
        String lastResponse,
        List<String> agentsUsed,
        Instant lastUpdated
    ) {}
}
```

In production, you'd replace the `ConcurrentHashMap` with **Redis**, **PostgreSQL**, or another shared store that survives process restarts.

---

## Step 7: Load Balancing for Stateful Conversations (Discussion)

### The Challenge

When you scale the Concierge to multiple replicas, stateful conversations break: message 1 hits replica A, message 2 hits replica B, and replica B has no context.

### Solution: Sticky Sessions via `contextId`

Look at `exercises/exercise-5-observability/nginx-sticky-sessions.conf`:

```nginx
upstream concierge_agents {
    server localhost:8090;
    server localhost:8091;
    server localhost:8092;
}

server {
    listen 80;
    location / {
        proxy_pass http://concierge_agents;
        # Route by contextId for session affinity
        proxy_set_header X-A2A-Context-Id $http_x_a2a_context_id;
    }
}
```

### Tradeoffs

| Approach | Pros | Cons |
|----------|------|------|
| **Sticky sessions** (route by contextId) | Simple, no shared state needed | Uneven load, lost state on replica failure |
| **Shared state store** (Redis/DB) | Any replica can serve any request | Adds latency, another dependency to operate |
| **Hybrid** | Best of both — sticky routing with shared fallback | More complex to implement |

For most A2A deployments, **shared state store + any-replica routing** is the recommended pattern. Use `contextId` as the key.

---

## Checkpoint

You now have:
- **OpenTelemetry tracing** across all agents (Java + Python)
- **Jaeger visualization** showing the full Concierge → Agent call chain
- **Custom spans** around LLM calls for performance insight
- **Understanding** of distributed state and load balancing patterns for production

### The Complete DevConf Concierge System

```
┌──────────────────────────────────────────────────────────────┐
│                     DevConf Concierge                        │
│                                                              │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │ Concierge│  │ Session Agent│  │ Travel Tips Agent     │  │
│  │ :8090    │──│ :8080 (Qkus) │  │ :9000 (Python)        │  │
│  │          │  │ :8081 (SBoot)│  │                       │  │
│  │          │  │ :8082 (WFly) │  │                       │  │
│  └──────────┘  └──────────────┘  └───────────────────────┘  │
│       │              │                    │                  │
│       └──────────────┼────────────────────┘                  │
│                      │                                       │
│              ┌───────▼───────┐                               │
│              │    Jaeger     │  ◄── All traces visible here  │
│              │    :16686     │                               │
│              └───────────────┘                               │
└──────────────────────────────────────────────────────────────┘
```

## Wrap-Up: What You Built Today

In two hours, you built a **production-grade A2A agent ecosystem**:

1. **Exercise 1** — Your first A2A agent: AgentCard for discovery, AgentExecutor for message handling, LangChain4j for intelligence
2. **Exercise 2** — The same agent on three runtimes (Quarkus, Spring Boot, WildFly), proving A2A is runtime-agnostic
3. **Exercise 3** — A Python agent in the Java mesh, proving A2A is language-independent
4. **Exercise 4** — An orchestrator that dynamically discovers, decomposes, dispatches, and aggregates across the mesh
5. **Exercise 5** — OpenTelemetry observability and production-readiness patterns

### Going Further

- **Security**: Add OAuth2/OIDC authentication to your agents using Keycloak (see the `a2a-samples/magic_8_ball_security` example)
- **Streaming**: Enable SSE streaming for real-time responses (`message/stream` instead of `message/send`)
- **Push Notifications**: Register webhooks so agents can proactively notify each other
- **Service Registry**: Replace hardcoded URLs with Consul or Kubernetes service discovery
- **MCP Integration**: Give agents access to external tools via MCP (see the `pizza-vibe` project for inspiration)

### Resources

- [A2A Protocol Specification](https://google.github.io/A2A/)
- [A2A Java SDK](https://github.com/a2aproject/a2a-java-sdk)
- [A2A Python SDK](https://github.com/a2aproject/a2a-python-sdk)
- [A2A Samples](https://github.com/a2aproject/a2a-samples)
- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [Pizza Vibe (multi-agent reference)](https://github.com/salaboy/pizza-vibe)
