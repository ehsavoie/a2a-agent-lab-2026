# Solution: Exercise 2 — Multi-Runtime Session Agents

Three implementations of the exact same Session Agent, each on a different Java runtime, proving that the A2A protocol decouples the interface from the framework.

## What's Included

| Runtime | Directory | Port | Key Difference |
|---------|-----------|------|----------------|
| **Quarkus** | `exercises/exercise-1-session-agent/` | 8080 | CDI `@Produces` + `@RegisterAiService` |
| **Spring Boot** | `exercises/exercise-2a-spring-boot/` | 8081 | `@Bean` + `AiServices.builder()` |
| **WildFly (Jakarta EE)** | `exercises/exercise-2b-ja2a/` | 8082 | WAR on WildFly 40, CDI + `a2a-jakarta-jsonrpc` |

All three expose identical AgentCards with the same skills — clients cannot tell which framework runs behind the A2A endpoint.

## Prerequisites

- Ollama running on `localhost:11434` with the `mistral` model pulled
- JDK 21+, Maven 3.9+

## How to Run

Open **three separate terminals** (or use the `run-all.sh` script):

**Terminal 1 — Quarkus (port 8080):**
```bash
cd ../../exercises/exercise-1-session-agent
mvn quarkus:dev
```

**Terminal 2 — Spring Boot (port 8081):**
```bash
cd ../../exercises/exercise-2a-spring-boot
mvn spring-boot:run
```

**Terminal 3 — WildFly (port 8082):**
```bash
cd ../../exercises/exercise-2b-ja2a
mvn package -Dsession.data.path=$(pwd)/../conference-data/sessions.json
./target/wildfly/bin/standalone.sh -Djboss.socket.binding.port-offset=2 \
  -Dsession.data.path=$(pwd)/../conference-data/sessions.json
```

Or simply:
```bash
./run-all.sh
```

## How to Verify

**1. Compare AgentCards across all three runtimes:**

```bash
for port in 8080 8081 8082; do
  echo "=== Port $port ==="
  curl -s "http://localhost:${port}/.well-known/agent.json" | python3 -c "
import sys, json
card = json.load(sys.stdin)
print(f\"  Name: {card['name']}\")
print(f\"  Skills: {[s['id'] for s in card.get('skills', [])]}\")
"
done
```

All three should show the same agent name and skill IDs.

**2. Send the same query to all three:**

```bash
for port in 8080 8081 8082; do
  echo "=== Port $port ==="
  curl -s -X POST "http://localhost:${port}" \
    -H "Content-Type: application/json" \
    -d '{
      "jsonrpc": "2.0",
      "method": "message/send",
      "params": {
        "message": {
          "role": "user",
          "parts": [{"type": "text", "text": "What sessions are about Kubernetes?"}]
        }
      },
      "id": "test-'$port'"
    }' | python3 -c "import sys,json; r=json.load(sys.stdin); print(r.get('result',{}).get('status',{}).get('state','ERROR'))"
done
```

All three should return `completed`.
