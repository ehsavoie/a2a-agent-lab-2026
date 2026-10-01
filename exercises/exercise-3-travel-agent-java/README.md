# Exercise 3 — Travel Agent Java Client

This directory contains the **Java A2A SDK client** that calls the Python Travel & Logistics Agent, proving Java → Python cross-language interoperability.

See [`../exercise-3-travel-agent-python/README.md`](../exercise-3-travel-agent-python/README.md) for the full Exercise 3 instructions, including setup of the Python agent, cross-language testing, and verification steps.

## Quick Start

Requires the Python Travel Agent to be running on port 9000 first:

```bash
# Terminal 1 — start the Python agent
cd exercises/exercise-3-travel-agent-python
python travel_agent.py

# Terminal 2 — run the Java client
cd exercises/exercise-3-travel-agent-java
mvn compile exec:java
```

## What to Implement

Open `src/main/java/dev/devconf/travel/TravelAgentClient.java` and implement the three steps in `main()`:

1. **Fetch the AgentCard** — `A2A.getAgentCard(PYTHON_AGENT_URL)`
2. **Build the client** — `Client.builder(card).withTransport(RestTransport.class, new RestTransportConfigBuilder()).build()`
3. **Send three queries** via the provided `sendAndPrint()` helper:
   - `"How do I get from Brussels Airport to Kinepolis Antwerp?"`
   - `"What is the status of flight UA 998?"`
   - `"Log my taxi receipt for €65"`
