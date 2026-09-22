# Solution: Exercise 3 — Travel & Logistics Agent (Python) + Java Client

The complete working **Travel & Logistics Agent** built with the A2A Python SDK, plus a **Java A2A client** that calls the Python agent — proving cross-language interop in both directions.

## What's Included

| File / Directory | Purpose |
|------------------|---------|
| `../../exercises/exercise-3-travel-agent-python/travel_agent.py` | A2A agent with flight, transit, hotel, restaurant, and receipt data |
| `../../exercises/exercise-3-travel-agent-python/test_interop.py` | Python → Java interop test (calls the Schedule Advisor on port 8080) |
| `../../exercises/exercise-3-travel-agent-python/java-client/` | Java → Python interop client using the A2A Java SDK |
| `../../exercises/exercise-3-travel-agent-python/pyproject.toml` | Python dependencies: `a2a-sdk`, `uvicorn`, `httpx` |

## Prerequisites

- Python 3.11+ with `pip` or `uv`
- JDK 21+ and Maven 3.9+ (for the Java client)
- **Schedule & Content Advisor** (Exercise 1) running on port 8080 (for the Python → Java interop test)

## How to Run

### Python Travel Agent

```bash
./run.sh
# Or manually:
cd ../../exercises/exercise-3-travel-agent-python
pip install -e .
python travel_agent.py
```

The agent starts on **port 9000**.

### Java → Python Client

With the Python agent running on port 9000:

```bash
cd ../../exercises/exercise-3-travel-agent-python/java-client
mvn compile exec:java
```

This fetches the Python agent's AgentCard, builds an A2A client, and sends three queries (transit, flight status, receipt extraction).

## How to Verify

```bash
# Check the AgentCard
curl -s http://localhost:9000/.well-known/agent-card.json | python3 -m json.tool

# Get transit options
curl -s -X POST http://localhost:9000/ \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "jsonrpc": "2.0",
    "method": "SendMessage",
    "params": {
      "message": {
        "messageId": "msg-1",
        "role": "ROLE_USER",
        "parts": [{"text": "My flight was delayed. How do I get to the convention center quickly?"}]
      }
    },
    "id": "1"
  }' | python3 -m json.tool

# Python → Java interop test
python test_interop.py

# Java → Python interop client
cd java-client && mvn compile exec:java
```
