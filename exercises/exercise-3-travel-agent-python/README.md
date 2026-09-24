# Exercise 3: Cross-Language — The Travel & Logistics Agent

**Time:** 15 minutes

> _"My plane ran late, and I am at Brussels Airport. How can I get to the convention center quickly?" The Travel & Logistics Agent compares rideshare, train, and taxi options — factoring in a transit disruption on the airport express — and recommends a Bolt rideshare for the 35-minute trip._

## What You Build

A Python A2A agent using the `a2a-sdk` that provides:
- Flight status with delay information
- Transit route comparison (rideshare, train, taxi, shuttle) with live disruption data
- Hotel search with availability and pricing
- Receipt extraction for expense reporting (cross-agent handoff to Exercise 5)
- Restaurant recommendations and local tips

## What This Proves

A2A is a wire protocol — **any language, any framework**. A Python agent joins a Java mesh with zero adapters. A Java client calls the Python agent the same way it calls a Java agent.

## Project Structure

```
exercise-3-travel-agent-python/
├── pyproject.toml      # Dependencies: a2a-sdk, uvicorn, httpx
├── travel_agent.py     # Agent implementation (AgentCard, handler, data)
├── test_interop.py     # Cross-language test: Python → Java Schedule Agent
└── java-client/        # Java → Python A2A interop client
```

## Prerequisites

- Python 3.11+
- `pip` or `uv`
- Exercise 1 Schedule & Content Advisor running on port 8080 (for the cross-language test)
- JDK 21+ and Maven 3.9+ (for the Java → Python client)

## How to Run

```bash
cd exercises/exercise-3-travel-agent-python

# Install dependencies
pip install -e .

# Start the agent
python travel_agent.py
```

The agent starts on **http://localhost:9000**.

## How to Verify

**1. Check the AgentCard:**

```bash
curl http://localhost:9000/.well-known/agent-card.json | python3 -m json.tool
```

You should see the agent's name, skills (`flight-status`, `transit-routes`, `hotel-search`, `receipt-extraction`, `restaurant-search`, `local-tips`), and capabilities.

**2. Send a transit query:**

```bash
curl -s -X POST http://localhost:9000/message:send \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "message": {
      "messageId": "msg-1",
      "role": "ROLE_USER",
      "parts": [{"text": "My plane ran late, and I am at Brussels Airport. How can I get to the convention center quickly?"}]
    }
  }' | python3 -m json.tool
```

**3. Test receipt extraction:**

```bash
curl -s -X POST http://localhost:9000/message:send \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "message": {
      "messageId": "msg-1",
      "role": "ROLE_USER",
      "parts": [{"text": "Log my taxi receipt for $42.50"}]
    }
  }' | python3 -m json.tool
```

## Cross-Language Test

For Python → Java interop, start the [Schedule & Content Advisor](../exercise-1-schedule-advisor/README.md#quick-start) on port 8080. In another terminal, from this project directory, run:

```bash
python test_interop.py
```

This fetches the Java agent's AgentCard and sends it a query — proving Python-to-Java A2A interop.

For Java → Python interop, keep the Travel Agent running on port 9000 and run the client in another terminal:

```bash
cd exercises/exercise-3-travel-agent-python/java-client
mvn compile exec:java
```

## Full Instructions

See [../../docs/exercise-3.md](../../docs/exercise-3.md) for the complete step-by-step guide.
