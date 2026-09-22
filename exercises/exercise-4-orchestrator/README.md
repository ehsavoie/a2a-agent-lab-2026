# Exercise 4: The Orchestrator & Concierge (Quarkus Native)

The primary edge router and user-facing gateway. Receives user prompts, inspects AgentCard schemas across the mesh, and orchestrates multi-agent tasks using LangChain4j for query decomposition and response aggregation.

## Quick Start

```bash
# Prerequisites: agents on ports 8080, 8081, 9000, 8082 must be running
mvn quarkus:dev
```

The Orchestrator starts on **port 8090**.

## Test

```bash
curl -s http://localhost:8090/.well-known/agent-card.json | jq .

curl -s -X POST http://localhost:8090/ \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "msg-1",
        "role": "ROLE_USER",
        "parts": [{"text": "My flight was delayed so I missed the morning shuttle. I am interested in agentic AI and Java agents. What talks should I catch today, how do I get to the venue quickly, and can you log my taxi receipt?"}]
      }
    },
    "id": "maya-1"
  }' | jq .
```
