# Exercise 3: Cross-Language — The Python Travel Tips Agent

**Time:** 15 minutes

> _The conference partners with a local tourism board. Their developer built a Travel Tips agent in Python — it recommends restaurants, transit routes, and local attractions. Can it join your Java agent mesh? With A2A, language doesn't matter._

## What You Build

A Python A2A agent using the `a2a-sdk` that provides:
- Restaurant recommendations near the convention center
- Transit information (airport, metro, taxi, parking)
- Local tips for conference attendees

## What This Proves

A2A is a wire protocol — **any language, any framework**. A Python agent joins a Java mesh with zero adapters. A Java client calls the Python agent the same way it calls a Java agent.

## Project Structure

```
exercise-3-travel-agent-python/
├── pyproject.toml      # Dependencies: a2a-sdk, uvicorn, httpx
├── travel_agent.py     # Agent implementation (AgentCard, handler, data)
└── test_interop.py     # Cross-language test: Python → Java Session Agent
```

## Prerequisites

- Python 3.11+
- `pip` or `uv`
- Exercise 1 Session Agent running on port 8080 (for the cross-language test)

## How to Run

```bash
cd exercise-3-travel-agent-python

# Install dependencies
pip install -e .

# Start the agent
python travel_agent.py
```

The agent starts on **http://localhost:9000**.

## How to Verify

**1. Check the AgentCard:**

```bash
curl http://localhost:9000/.well-known/agent.json | python3 -m json.tool
```

You should see the agent's name, skills (`restaurant-search`, `transit-info`, `local-tips`), and capabilities.

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

## Cross-Language Test

With the Java Session Agent running on port 8080:

```bash
python test_interop.py
```

This fetches the Java agent's AgentCard and sends it a query — proving Python-to-Java A2A interop.

## Full Instructions

See [../../docs/exercise-3.md](../../docs/exercise-3.md) for the complete step-by-step guide.
