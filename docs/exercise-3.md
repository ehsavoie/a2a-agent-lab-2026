# Exercise 3: Cross-Language — The Python Travel Tips Agent (15 min)

> _"The conference partners with a local tourism board. Their developer built a Travel Tips agent in Python — it recommends restaurants, transit routes, and local attractions. Can it join your Java agent mesh? With A2A, language doesn't matter."_

## Overview

In this exercise you will prove that A2A is truly language-independent. You will:

- Run a Python-based A2A agent using the `a2a-python-sdk`
- Call it from your Java A2A client
- Call your Java Session Agent from a Python client

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
uv sync
```

Take a look at `pyproject.toml` to see what we're pulling in:

```toml
[project]
dependencies = [
    "a2a-sdk>=0.2.0",      # The official A2A Python SDK
    "uvicorn>=0.30.0",      # ASGI server to host our agent
    "httpx>=0.27.0",        # HTTP client for cross-agent calls
]
```

Notice the symmetry with the Java side: just like we used `a2a-java-sdk`, Python uses `a2a-sdk`. The protocol is the same — only the language binding differs.

---

## Step 2: Explore the Agent Code

Open `travel_agent.py` and walk through it. You'll recognize the same A2A concepts from Exercise 1, expressed in Python:

### Travel Data

The agent has a built-in knowledge base of restaurants, transit info, and local tips — all hardcoded for the lab. In production, this could call external APIs.

```python
TRAVEL_DATA = {
    "restaurants": [ ... ],
    "transit": { ... },
    "local_tips": [ ... ],
}
```

### Query Handling

The `answer_query()` function routes user questions to the right data source based on keywords:

```python
def answer_query(query: str) -> str:
    query_lower = query.lower()
    if any(w in query_lower for w in ["eat", "food", "restaurant", ...]):
        return search_restaurants(query)
    if any(w in query_lower for w in ["transit", "metro", "airport", ...]):
        return get_transit_info(query)
    ...
```

### AgentExecution (Python equivalent of AgentExecutor)

The `TravelAgentExecution` class implements the A2A message handler — the Python equivalent of the `AgentExecutor` you built in Java:

```python
class TravelAgentExecution(AgentExecution):
    async def execute(self, context: RequestContext, event_queue) -> None:
        # Extract text from the incoming message parts
        user_message = ""
        for part in context.message.parts:
            if isinstance(part.root, TextPart):
                user_message = part.root.text
                break

        # Process and respond
        response_text = answer_query(user_message)
        await event_queue.enqueue_event(
            context.build_success_response(
                parts=[Part(root=TextPart(text=response_text))]
            )
        )
```

Compare this to your Java `AgentExecutor.execute(RequestContext, AgentEmitter)` — the pattern is identical:
1. Extract text from the message parts
2. Process the query
3. Return a response with text parts

### AgentCard

The `build_agent_card()` function creates the agent's identity — same concept as your `SessionAgentCardProducer`:

```python
def build_agent_card() -> AgentCard:
    return AgentCard(
        name="Travel Tips Agent",
        description="Provides local travel tips...",
        url="http://localhost:9000",
        skills=[ AgentSkill(id="restaurant-search", ...), ... ],
        ...
    )
```

### Server Startup

The `main()` function wires everything together and starts the A2A server:

```python
def main():
    agent_card = build_agent_card()
    agent_execution = TravelAgentExecution()
    request_handler = DefaultRequestHandler(
        agent_execution=agent_execution, agent_card=agent_card,
    )
    app = A2AStarletteApplication(
        agent_card=agent_card, http_handler=request_handler,
    )
    uvicorn.run(app.build(), host="0.0.0.0", port=9000)
```

---

## Step 3: Run the Python Agent

Start the Travel Tips Agent:

```bash
python travel_agent.py
```

You should see:

```
🌍 Travel Tips Agent starting on http://localhost:9000
📋 Agent Card: http://localhost:9000/.well-known/agent.json
```

### Test the AgentCard

```bash
curl -s http://localhost:9000/.well-known/agent.json | python -m json.tool
```

You should see the AgentCard with skills like `restaurant-search` and `transit-info` — the same JSON structure as your Java agents.

### Test a message

```bash
curl -s -X POST http://localhost:9000/ \
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
    "id": "test-1"
  }' | python -m json.tool
```

You should get a Task response with restaurant recommendations as a text artifact.

---

## Step 4: Java → Python

Now let's call the Python agent from Java. Make sure your Quarkus Session Agent from Exercise 1 is still running on port 8080.

You can use `jshell` or add this as a test class. Here's the A2A client call:

```java
import org.a2aproject.sdk.spec.*;
import org.a2aproject.sdk.client.A2AClient;
import java.util.List;

// Connect to the Python Travel Tips Agent
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
    .parts(List.of(new TextPart("What vegetarian restaurants are nearby?")))
    .build();

Task result = travelClient.sendMessage(message);
System.out.println("Response: " + result);
```

The key insight: **the Java A2A client code is identical whether the server is Java or Python.** The client doesn't know or care about the server's implementation language.

---

## Step 5: Python → Java

Now let's go the other direction. Run the interop test script that calls your Java Session Agent from Python:

```bash
python test_interop.py
```

You should see output like:

```
============================================================
Python → Java A2A Interop Test
============================================================

1. Fetching Java Session Agent's AgentCard...
   Agent: Session Recommender Agent
   Skills: ['Session Search', 'Session Recommendations']

2. Sending message to Java Session Agent...
   Response: { ... }

✅ Cross-language interop successful!
   Python client → Java A2A agent → response received
```

Open `test_interop.py` to see how it works — it's a simple HTTP client that speaks the A2A JSON-RPC protocol:

```python
async def send_message(base_url: str, text: str) -> dict:
    payload = {
        "jsonrpc": "2.0",
        "method": "message/send",
        "params": {
            "message": {
                "role": "user",
                "parts": [{"type": "text", "text": text}],
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

All agents — regardless of language — advertise their capabilities through AgentCards and handle messages through the same JSON-RPC protocol. The mesh is now truly polyglot.

### Running agents so far

| Agent | Language | Port | Status |
|-------|----------|------|--------|
| Session Agent (Quarkus) | Java | 8080 | Running from Exercise 1 |
| Session Agent (Spring Boot) | Java | 8081 | Running from Exercise 2 |
| Session Agent (WildFly) | Java | 8082 | Running from Exercise 2 |
| **Travel Tips Agent** | **Python** | **9000** | **New in this exercise** |

Keep the Session Agent (8080) and Travel Tips Agent (9000) running — the Concierge in Exercise 4 will orchestrate them both.
