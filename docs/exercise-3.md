# Exercise 3: Cross-Language — The Travel & Logistics Agent (15 min)

> _"Maya's flight was delayed by two hours. She opens her DevSphere app and types: 'How do I get to the venue quickly?' The Travel & Logistics Agent — written in Python — compares a Bolt rideshare (35 min, €45) vs the disrupted NMBS train (55 min, €12) and recommends the rideshare. Language doesn't matter — A2A is a wire protocol."_

## Overview

In this exercise you will prove that A2A is truly language-independent. You will:

- Run a Python-based A2A agent using the `a2a-python-sdk`
- Explore its expanded capabilities: flight status, transit comparison, hotel search, and receipt extraction
- Call it from your Java A2A client
- Call your Java Schedule & Content Advisor from a Python client

The key learning: **A2A is a wire protocol** — any language that speaks JSON-RPC over HTTP can participate in the mesh. Your Java agents and this Python agent are fully interoperable.

---

## Step 1: Set Up the Python Project

Open a **new terminal tab** and navigate to the Python agent directory:

```bash
cd exercises/exercise-3-travel-agent-python
```

Install the dependencies. You can use `pip` or `uv`:

```bash
# Option A: Using pip
python -m venv .venv
source .venv/bin/activate
pip install -e .

# Option B: Using uv (faster)
uv venv
uv sync
```

Take a look at `pyproject.toml` to see what we're pulling in:

```toml
[project]
dependencies = [
    "a2a-sdk[http-server]>=0.2.0",  # The official A2A Python SDK (with Starlette HTTP server)
    "uvicorn>=0.30.0",      # ASGI server to host our agent
    "httpx>=0.27.0",        # HTTP client for cross-agent calls
]
```

Notice the symmetry with the Java side: just like we used `a2a-java-sdk`, Python uses `a2a-sdk`. The protocol is the same — only the language binding differs.

---

## Step 2: Explore the Agent Code

Open `travel_agent.py` and walk through it. You'll recognize the same A2A concepts from Exercise 1, expressed in Python.

### Travel & Logistics Data

The agent has an expanded knowledge base covering Maya's full travel lifecycle:

```python
TRAVEL_DATA = {
    "flights": [
        {
            "flight": "UA 998", "origin": "EWR", "destination": "BRU",
            "scheduled_arrival": "06:30", "actual_arrival": "08:35",
            "delay_minutes": 125, "status": "DELAYED",
            "gate": "B44", "terminal": "Terminal A",
            "note": "Delayed due to late-arriving aircraft from Chicago.",
        },
        ...
    ],
    "transit": {
        "options": [
            {"mode": "Rideshare (Bolt/Uber)", "duration_minutes": 35, "cost": "€45",
             "disruption": None, ...},
            {"mode": "Train (Brussels Airport → Antwerp-Centraal)", "duration_minutes": 35, "cost": "€12",
             "disruption": "Track works — estimated 55 min today.", ...},
            ...
        ]
    },
    "hotels": [...],
    "restaurants": [...],
    "local_tips": [...],
}
```

In production, these would connect to real flight APIs (FlightAware), transit feeds (GTFS-RT), and hotel booking systems.

### Transit Comparison Logic

The `get_transit_options()` function compares all available modes and factors in disruptions:

```python
def get_transit_options(query: str) -> str:
    lines = ["**Transit options from Brussels Airport (BRU) to Kinepolis Antwerp:**\n"]
    best_option = None
    best_time = 999

    for opt in TRAVEL_DATA["transit"]["options"]:
        effective_time = opt["duration_minutes"]
        if opt["disruption"]:
            if "55 min" in opt["disruption"]:
                effective_time = 55  # Adjust for disruption

        if effective_time < best_time:
            best_time = effective_time
            best_option = opt["mode"]
        ...

    lines.append(f"Recommendation: {best_option} is the fastest option today (~{best_time} min).")
```

This is what makes Maya's scenario work: the NMBS train is normally 35 minutes, but today's track works between Mechelen and Antwerp bump it to 55 minutes — so the Bolt rideshare at 35 minutes wins.

### Receipt Extraction (Key for Exercise 5)

The `extract_receipt()` function simulates extracting fare details from a description and outputs a structured payload:

```python
def extract_receipt(query: str) -> str:
    # Parse vendor type from keywords
    if any(w in query_lower for w in ["taxi", "cab"]):
        vendor = "Antwerp Taxi Service"
        amount = "65.00"
        category = "ground_transportation"
    ...

    receipt = {
        "vendor": vendor,
        "amount": amount,
        "currency": "EUR",
        "date": date,
        "category": category,
        "status": "pending_review",
    }
```

This structured payload is exactly what the Expense & Compliance Agent (Exercise 5) will consume for audit-ready processing — demonstrating **cross-agent, cross-language data handoff**.

### AgentExecutor (Python equivalent of the Java AgentExecutor)

The `TravelAgentExecutor` class implements the A2A message handler — the Python equivalent of the `AgentExecutor` you built in Java:

```python
class TravelAgentExecutor(AgentExecutor):
    async def execute(self, context: RequestContext, event_queue: EventQueue) -> None:
        user_message = context.get_user_input()
        if not user_message:
            user_message = "general"

        response_text = answer_query(user_message)

        await event_queue.enqueue_event(
            Message(
                role=Role.ROLE_AGENT,
                parts=[Part(text=response_text)],
                message_id=str(uuid.uuid4()),
            )
        )

    async def cancel(self, context: RequestContext, event_queue: EventQueue) -> None:
        pass
```

Compare this to your Java `AgentExecutor.execute(RequestContext, AgentEmitter)` — the pattern is identical:
1. Extract text from the message using `context.get_user_input()`
2. Process the query
3. Return a `Message` response with text parts via the event queue

### AgentCard — Six Skills

The `build_agent_card()` function registers six skills covering the full travel lifecycle:

```python
skills=[
    AgentSkill(id="flight-status",      name="Flight Status", ...),
    AgentSkill(id="transit-routes",     name="Transit Routes", ...),
    AgentSkill(id="hotel-search",       name="Hotel Search", ...),
    AgentSkill(id="receipt-extraction", name="Receipt Extraction", ...),
    AgentSkill(id="restaurant-search",  name="Restaurant Search", ...),
    AgentSkill(id="local-tips",         name="Local Tips", ...),
]
```

---

## Step 3: Run the Python Agent

Start the Travel & Logistics Agent:

```bash
python travel_agent.py
```

You should see:

```
✈️  Travel & Logistics Agent starting on http://localhost:9000
📋 Agent Card: http://localhost:9000/.well-known/agent-card.json
```

### Test the AgentCard

```bash
curl -s http://localhost:9000/.well-known/agent-card.json | python -m json.tool
```

You should see the AgentCard with skills like `flight-status`, `transit-routes`, `receipt-extraction` — the same JSON structure as your Java agents.

### Test Maya's scenario: transit comparison (REST transport)

```bash
curl -s -X POST http://localhost:9000/message:send \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "message": {
      "messageId": "msg-1",
      "role": "ROLE_USER",
      "parts": [{"text": "My flight was delayed. How do I get to Kinepolis Antwerp quickly?"}]
    }
  }' | python -m json.tool
```

You should get a response comparing all transit options, flagging the NMBS train disruption, and recommending the Bolt rideshare.

### Test receipt extraction

```bash
curl -s -X POST http://localhost:9000/message:send \
  -H "Content-Type: application/json" \
  -H "A2A-Version: 1.0" \
  -d '{
    "message": {
      "messageId": "msg-1",
      "role": "ROLE_USER",
      "parts": [{"text": "Log my taxi receipt for €65"}]
    }
  }' | python -m json.tool
```

You should see a structured receipt payload with vendor, amount (EUR), date, and category — ready for the Expense & Compliance Agent.

---

## Step 4: Java → Python

Now let's call the Python agent from Java. Make sure your Schedule & Content Advisor from Exercise 1 is still running on port 8080.

You can use `jshell` or add this as a test class. Here's the A2A client call:

```java
import org.a2aproject.sdk.spec.*;
import org.a2aproject.sdk.client.A2AClient;
import java.util.List;

// Connect to the Python Travel & Logistics Agent
A2AClient travelClient = A2AClient.builder()
    .url("http://localhost:9000")
    .build();

// Fetch its AgentCard — same structure as your Java agents
AgentCard card = travelClient.getAgentCard();
System.out.println("Agent: " + card.name());
System.out.println("Skills: " + card.skills());

// Send a message
Message message = Message.builder()
    .role("user")
    .parts(List.of(new TextPart("How do I get from the airport to the venue?")))
    .build();

Task result = travelClient.sendMessage(message);
System.out.println("Response: " + result);
```

The key insight: **the Java A2A client code is identical whether the server is Java or Python.** The client doesn't know or care about the server's implementation language.

---

## Step 5: Python → Java

Now let's go the other direction. Run the interop test script that calls your Java Schedule & Content Advisor from Python:

```bash
python test_interop.py
```

You should see output like:

```
============================================================
Python → Java A2A Interop Test
============================================================

1. Fetching Java Schedule & Content Advisor's AgentCard...
   Agent: Schedule & Content Advisor
   Skills: ['Session Search', 'Session Recommendations', 'Speaker Info']

2. Sending message to Java Schedule & Content Advisor...
   Response: { ... }

✅ Cross-language interop successful!
   Python client → Java A2A agent → response received
```

Open `test_interop.py` to see how it works — it's a simple HTTP client that speaks the A2A JSON-RPC protocol:

```python
async def send_message(base_url: str, text: str) -> dict:
    payload = {
        "jsonrpc": "2.0",
        "method": "SendMessage",
        "params": {
            "message": {
                "messageId": "msg-1",
                "role": "ROLE_USER",
                "parts": [{"text": text}],
            }
        },
        "id": "py-test-1",
    }
    async with httpx.AsyncClient(timeout=60.0) as client:
        response = await client.post(base_url, json=payload)
        return response.json()
```

---

## Checkpoint

You now have:
- A **Python A2A agent** running alongside your Java agents
- **Java → Python** communication via the A2A client
- **Python → Java** communication using the same protocol
- **Receipt extraction** that outputs structured data for cross-agent handoff (Exercise 5)

All agents — regardless of language — advertise their capabilities through AgentCards and handle messages through the same JSON-RPC protocol. The mesh is now truly polyglot.

### Running agents so far

| Agent | Language | Framework | Port | Status |
|-------|----------|-----------|------|--------|
| Schedule & Content Advisor | Java | Jakarta EE / Java A2A SDK | 8080 | Running from Exercise 1 |
| Venue & On-Site Operations Agent | Java | Spring Boot | 8081 | Running from Exercise 2 |
| **Travel & Logistics Agent** | **Python** | **a2a-sdk** | **9000** | **New in this exercise** |

Keep all three agents running — the Orchestrator in Exercise 4 will coordinate them all.

---

> **Maya's journey so far:** The Travel & Logistics Agent has told Maya that a Bolt rideshare is 35 minutes from Brussels Airport to Kinepolis Antwerp (€45), much faster than the disrupted NMBS train (55 min). Her estimated arrival at the venue: 9:45 AM. This arrival time will be passed to the Schedule & Content Advisor (via the Orchestrator) to filter out sessions she's already missed.
