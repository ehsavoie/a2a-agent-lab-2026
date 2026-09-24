# Solution: Exercise 2 — Venue & On-Site Operations Agent (Spring Boot + LangChain4j)

The complete working **Venue & On-Site Operations Agent** built with Spring Boot, LangChain4j, and [spring-a2a](https://github.com/Sh1bari/spring-a2a), managing real-time IoT room capacity, indoor navigation, and catering tracking.

## What's Included

| File | Purpose |
|------|---------|
| `VenueTool.java` | `@Tool` methods for room capacity (IoT sensors), indoor directions, catering, entry passes |
| `VenueService.java` | LangChain4j AI Service interface with `@SystemMessage` |
| `VenueAgentConfig.java` | Spring `@Configuration` with `@Bean` for AgentCard + AgentExecutor (using `AiServices.builder`) |
| `VenueAgentApplication.java` | Spring Boot main class |
| `application.properties` | Port 8081, OpenAI model and reasoning config, agent metadata |

## Prerequisites

- `OPENAI_API_KEY` environment variable set with an OpenAI API key with API billing enabled for `gpt-6-luna` through the Responses API at medium reasoning effort. ChatGPT subscriptions do not cover API usage, and the GPT-6 Luna API Free tier is unsupported.
- JDK 21+, Maven 3.9+

## How to Run

```bash
export OPENAI_API_KEY=your-api-key-here
# From the repository root, run the matching solution:
cd solutions/exercise-2
mvn spring-boot:run
```

`run-all.sh` launches the exercise project under `exercises/`. From the repository root, use it to run that version instead:

```bash
export OPENAI_API_KEY=your-api-key-here
./solutions/exercise-2/run-all.sh
```

## How to Verify

```bash
# Check the AgentCard
curl -s http://localhost:8081/.well-known/agent-card.json | jq .

# Check room capacity (REST transport)
curl -s -X POST http://localhost:8081/message:send \
  -H 'Content-Type: application/json' \
  -H 'A2A-Version: 1.0' \
  -d '{
    "message": {
      "messageId": "msg-1",
      "role": "ROLE_USER",
      "parts": [{"text": "Is Hall B full? Can I get a fast-track entry pass?"}]
    }
  }' | jq .
```
