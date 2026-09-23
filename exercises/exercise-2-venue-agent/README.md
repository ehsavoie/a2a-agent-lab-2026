# Exercise 2: Venue & On-Site Operations Agent (Spring Boot + LangChain4j)

A Spring Boot A2A agent using [spring-a2a](https://github.com/Sh1bari/spring-a2a) that simulates venue operations: IoT-style room capacity readings, indoor navigation, catering queue tracking, and fast-track entry pass reservations.

## Quick Start

Requires JDK 21+, Maven 3.9+, and an OpenAI API key with API billing enabled in `OPENAI_API_KEY`. This agent uses `gpt-6-luna` through the Responses API at medium reasoning effort. ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.

```bash
# From the repository root:
export OPENAI_API_KEY=your-api-key-here
cd exercises/exercise-2-venue-agent
mvn spring-boot:run
```

Agent starts on **port 8081**.

## Verify

```bash
# AgentCard
curl -s http://localhost:8081/.well-known/agent-card.json | jq .

# Send a message (REST)
curl -s -X POST http://localhost:8081/message:send \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{
    "message": {
      "messageId": "msg-1",
      "role": "ROLE_USER",
      "parts": [{"text": "Is Hall B full?"}]
    }
  }' | jq .
```

## Skills

| Skill | Description |
|-------|-------------|
| `room-capacity` | Check real-time room capacity from IoT sensors |
| `indoor-map` | Get walking directions between venue locations |
| `catering-queue` | Check catering station queue lengths and wait times |
| `entry-pass` | Reserve a fast-track entry pass for priority seating |

## Full Instructions

See [../../docs/exercise-2.md](../../docs/exercise-2.md) for the complete step-by-step guide.
