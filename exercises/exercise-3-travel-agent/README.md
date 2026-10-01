# Exercise 3 — Cross-Language Travel & Logistics Agent (15 min)

> *"Maya's flight was delayed. She needs to get from Brussels Airport to Kinepolis Antwerp fast. The Travel Agent compares a Bolt rideshare (35 min, €45) vs the disrupted NMBS train (55 min, €12) and recommends the rideshare. Later, it extracts her taxi receipt for expense reporting."*

Build a **Python A2A SDK** agent and prove cross-language interoperability — a Java client calling a Python agent and a Python client calling a Java agent, all with identical A2A protocol calls.

This exercise has two parts:
- **`exercise-3-travel-agent/`** — the Python agent (this README)
- **`java-client/`** — a Java client that calls the Python agent (inside this directory)

## Context

> **No LLM here.** Unlike the Java agents in Exercises 1 and 2, the Travel Agent does **not** use an LLM. It is a **rule-based agent** that pattern-matches keywords against hardcoded travel data. This is intentional: it shows that A2A agents don't need to be "AI-powered" to participate in the mesh. Any service that speaks the A2A protocol is a first-class citizen.

The `receipt-extraction` skill outputs structured data that the Expense & Compliance Agent (Exercise 4) can consume — this is the cross-agent data handoff in Maya's scenario.

## What You Will Explore

The Travel Agent is **fully implemented** — this exercise is about understanding the code and proving cross-language interoperability.

Open `travel_agent.py` and study the six skill areas:

| Skill ID | Description |
|---|---|
| `flight-status` | Check flight status, delays, and gate information |
| `transit-routes` | Compare transit options from Brussels Airport to Kinepolis Antwerp |
| `hotel-search` | Hotel recommendations near the venue |
| `receipt-extraction` | Extract structured receipt data for expense reporting |
| `restaurant-search` | Nearby restaurant recommendations |
| `local-tips` | Local tips for the conference area |

The `get_transit_options()` function returns a comparison:
- **Rideshare (Bolt/Uber):** 35 min, €45 — recommended when train is disrupted
- **Train (NMBS):** normally 35 min, €12 — currently disrupted (55 min today)
- **Taxi (fixed fare):** 40 min, €65–75
- **Rental car:** 40 min, €55/day

The `extract_receipt()` function returns structured JSON data:
```json
{"vendor": "...", "amount": "...", "currency": "EUR",
 "date": "...", "category": "...", "status": "pending_review"}
```

## Step 1 — Set Up and Start the Python Agent

```bash
cd exercises/exercise-3-travel-agent

# Option A: pip
python -m venv .venv
source .venv/bin/activate
pip install -e .

# Option B: uv (faster)
uv venv
uv sync

# Start the agent
python travel_agent.py
```

The agent starts on **port 9000**.

## Step 2 — Cross-Language Interoperability

### Python → Java (test_interop.py)

> **Prerequisite:** the Exercise 1 Schedule Advisor must be running on port 8080 first.

```bash
python test_interop.py
```

This calls the Java Schedule Advisor (Quarkus, port 8080) from Python using the A2A Python SDK — proving that a Python client can talk to a Java server.

### Test transit comparison (REST transport)

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

## Step 3 — Java → Python: Build the A2A Java Client

The Java client lives in `java-client/` inside this directory. It proves the reverse direction: calling the Python Travel Agent from Java using the **A2A Java SDK client**.

Open `TravelAgentClient.java` and implement the three steps in `main()`:

**Step 1 — Fetch the AgentCard:**
```java
AgentCard card = A2A.getAgentCard(PYTHON_AGENT_URL);
// Print card.name(), card.description(), and iterate card.skills()
```

**Step 2 — Build the client:**
```java
Client client = Client.builder(card)
    .withTransport(RestTransport.class, new RestTransportConfigBuilder())
    .build();
```

**Step 3 — Send queries** (call `sendAndPrint()` three times):
- `"How do I get from Brussels Airport to Kinepolis Antwerp?"`
- `"What is the status of flight UA 998?"`
- `"Log my taxi receipt for €65"`

The `sendAndPrint()` helper method is already provided.

```bash
cd exercises/exercise-3-travel-agent/java-client
mvn compile exec:java
```

Expected output:
```
==========================================================
Java → Python A2A Interop Client
==========================================================

1. Fetching Python Travel Agent's AgentCard...
   Agent: Travel & Logistics Agent
   Skills:
     - Flight Status: Check flight status, delays, and gate information.
     - Transit Routes: Get transit options from airport to venue...
     ...

2. Sending transit query: 'How do I get from the airport to the venue quickly?'
   Response:
   Here are your transit options:
   ...

✓ Java → Python A2A interop successful!
```

> **Key insight:** The Java client uses the exact same `A2A.getAgentCard()` / `Client.builder(card)` / `client.sendMessage()` API it would use to call a Java agent. It doesn't know or care that the server is Python.

## Checkpoint

- [ ] Python Travel & Logistics Agent running on port 9000
- [ ] Java → Python communication via the A2A Java SDK client
- [ ] Python → Java communication via `test_interop.py`
- [ ] Transit comparison returning Bolt rideshare recommendation
- [ ] Receipt extraction returning structured JSON data

## Agent Summary

| Agent | Language | Framework | Port |
|---|---|---|---|
| Schedule & Content Advisor | Java | Quarkus + A2A Java SDK | 8080 |
| Venue & On-Site Operations | Java | Spring Boot + spring-a2a | 8081 |
| **Travel & Logistics (Python)** | **Python** | **A2A Python SDK** | **9000** |
| Travel & Logistics (Java client) | Java | A2A Java SDK client | *(client only)* |

> **Note:** In Exercise 5, the Travel agent is bundled inside the Concierge — no need to keep it running separately.
