# Solution: Exercise 3 — Python Travel Tips Agent

The complete working **Travel Tips Agent** built with the A2A Python SDK, plus cross-language interop tests proving Java and Python agents talk seamlessly over A2A.

## What's Included

The full source lives in `../../exercises/exercise-3-travel-agent-python/`:

| File | Purpose |
|------|---------|
| `travel_agent.py` | A2A agent with restaurant, transit, and local tips data |
| `test_interop.py` | Python → Java interop test (calls the Session Agent on port 8080) |
| `pyproject.toml` | Dependencies: `a2a-sdk`, `uvicorn`, `httpx` |

## Prerequisites

- Python 3.11+ with `pip` or `uv`
- **Session Agent** (Exercise 1) running on port 8080 (for the interop test)

## How to Run

```bash
./run.sh
# Or manually:
cd ../../exercises/exercise-3-travel-agent-python
pip install -e .
python travel_agent.py
```

The agent starts on **port 9000**.

## How to Verify

**1. Check the AgentCard:**

```bash
curl -s http://localhost:9000/.well-known/agent.json | python3 -m json.tool
```

You should see skills: `restaurant-search`, `transit-info`, `local-tips`.

**2. Send a message:**

```bash
curl -s -X POST http://localhost:9000 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "message/send",
    "params": {
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "Where should I eat near the convention center?"}]
      }
    },
    "id": "1"
  }' | python3 -m json.tool
```

**3. Cross-language interop (Python → Java):**

Make sure the Session Agent is running on port 8080, then:

```bash
cd ../../exercises/exercise-3-travel-agent-python
python test_interop.py
```

**4. Cross-language interop (Java → Python):**

From any Java project, use the A2A client:

```java
A2AClient client = A2AClient.builder().url("http://localhost:9000").build();
AgentCard card = client.getAgentCard();
// card.name() == "Travel Tips Agent"
```
