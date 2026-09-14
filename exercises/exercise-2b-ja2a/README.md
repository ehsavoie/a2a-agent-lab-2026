# Exercise 2b: WildFly + A2A Jakarta EE SDK

**Part of Exercise 2: Multi-Runtime (20 minutes total)**

> _"The enterprise team runs WildFly in production. They want the same Session Agent deployed as a standard Jakarta EE WAR. The A2A Jakarta EE SDK makes this possible — same CDI producers, same AgentCard contract, deployed on WildFly."_

## What You Build

The same Session Recommender Agent from Exercise 1, repackaged as a WAR deployed on **WildFly 40** using the **A2A Jakarta EE SDK** (`a2a-jakarta-jsonrpc`).

## Key Differences from Exercise 1 (Quarkus)

| Aspect | Exercise 1 (Quarkus) | Exercise 2b (WildFly) |
|--------|---------------------|----------------------|
| Runtime | Quarkus dev mode | WildFly 40 (provisioned by Maven) |
| SDK | `a2a-java-sdk-reference-jsonrpc` | `a2a-jakarta-jsonrpc` + `a2a-java-sdk-server-common` |
| Packaging | JAR (Quarkus uber-jar) | WAR (deployed as ROOT.war) |
| LangChain4j | Quarkus extension (`quarkus-langchain4j-ollama`) | Plain LangChain4j library (`langchain4j-ollama`) |
| CDI | Quarkus Arc | Jakarta CDI (WildFly Weld) |
| Port | 8080 | 8082 (via port offset) |

## Project Structure

```
exercise-2b-ja2a/
├── pom.xml                                    # WAR packaging + WildFly provisioning
└── src/main/
    ├── java/dev/devconf/session/
    │   ├── SessionAgentApplication.java       # JAX-RS @ApplicationPath
    │   ├── SessionAgentCardProducer.java      # CDI @Produces @PublicAgentCard
    │   ├── SessionAgentExecutorProducer.java  # CDI @Produces AgentExecutor
    │   ├── SessionServiceProducer.java        # LangChain4j AI Service wiring
    │   ├── SessionService.java                # LangChain4j AI Service interface
    │   └── SessionTool.java                   # @Tool methods for session search
    └── resources/META-INF/
        ├── beans.xml                          # CDI bean discovery
        └── microprofile-config.properties     # A2A config (auth disabled)
```

## How to Build and Run

```bash
cd exercise-2b-ja2a

# Build the WAR and provision WildFly
mvn package -Dsession.data.path=$(pwd)/../../conference-data/sessions.json

# Start WildFly with the deployed agent (port offset puts it on 8082)
./target/wildfly/bin/standalone.sh -Djboss.socket.binding.port-offset=2 \
  -Dsession.data.path=$(pwd)/../../conference-data/sessions.json
```

## How to Verify

```bash
# Fetch the AgentCard
curl -s http://localhost:8082/.well-known/agent.json | jq .

# Send a message
curl -s -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "message/send",
    "params": {
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "What AI sessions are available?"}]
      }
    },
    "id": "test-wildfly"
  }' | jq .
```

## Cross-Runtime Comparison

With all three agents running (ports 8080, 8081, 8082), verify they serve equivalent AgentCards:

```bash
for port in 8080 8081 8082; do
  echo "=== Port $port ==="
  curl -s "http://localhost:${port}/.well-known/agent.json" \
    | jq '{name: .name, skills: [.skills[].name]}'
done
```

Same AgentCard skills, same response format — three runtimes, one protocol.

## Step-by-Step Guide

See [../../docs/exercise-2.md](../../docs/exercise-2.md) for the full walkthrough.
