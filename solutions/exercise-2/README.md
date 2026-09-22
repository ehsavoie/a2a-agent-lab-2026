# Solution: Exercise 2 — Venue & On-Site Operations Agent (Spring Boot + LangChain4j)

The complete working **Venue & On-Site Operations Agent** built with Spring Boot, LangChain4j, and [spring-a2a](https://github.com/Sh1bari/spring-a2a), managing real-time IoT room capacity, indoor navigation, and catering tracking.

## What's Included

| File | Purpose |
|------|---------|
| `VenueTool.java` | `@Tool` methods for room capacity (IoT sensors), indoor directions, catering, entry passes |
| `VenueService.java` | LangChain4j AI Service interface with `@SystemMessage` |
| `VenueAgentConfig.java` | Spring `@Configuration` with `@Bean` for AgentCard + AgentExecutor (using `AiServices.builder`) |
| `VenueAgentApplication.java` | Spring Boot main class |
| `application.properties` | Port 8081, LangChain4j Ollama config, agent metadata |

## Prerequisites

- Ollama running on `localhost:11434` with the `granite4.1:3b` model pulled
- JDK 21+, Maven 3.9+

## How to Run

```bash
./run-all.sh
# Or manually:
mvn spring-boot:run
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
