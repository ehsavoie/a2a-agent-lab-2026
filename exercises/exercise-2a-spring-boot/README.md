# Exercise 2a: Session Agent on Spring Boot

**Part of Exercise 2: Multi-Runtime (20 minutes total)**

Same Session Recommender agent, now running on Spring Boot instead of Quarkus — proving that A2A is runtime-agnostic.

## What This Proves

The **AgentCard is the contract**, not the framework. A client calling `/.well-known/agent.json` gets the same skills and capabilities whether the agent runs on Quarkus, Spring Boot, or a bare-metal embedded server. The A2A protocol decouples interface from implementation.

## Key Differences from Quarkus

| Concern | Quarkus (Exercise 1) | Spring Boot (This Exercise) |
|---------|----------------------|----------------------------|
| DI annotation | `@ApplicationScoped` | `@Component` |
| Bean producer | `@Produces` + `@PublicAgentCard` | `@Bean` in `@Configuration` |
| AI Service | `@RegisterAiService` (declarative) | `AiServices.builder()` (programmatic) |
| Config injection | `@ConfigProperty` | `@Value` |
| Run command | `mvn quarkus:dev` | `mvn spring-boot:run` |
| Port | 8080 | **8081** |

## Project Structure

```
src/main/java/dev/devconf/session/
├── SessionAgentSpringApplication.java  # @SpringBootApplication entry point
├── SessionTool.java                    # @Component with same @Tool methods
├── SessionService.java                 # LangChain4j AI Service interface
└── SessionAgentConfig.java             # @Configuration: SessionService, AgentCard, AgentExecutor beans

src/main/resources/
└── application.properties              # Port 8081, Ollama config
```

## How to Run

```bash
cd exercise-2a-spring-boot
mvn spring-boot:run
```

## How to Verify

```bash
# AgentCard
curl -s http://localhost:8081/.well-known/agent.json | python3 -m json.tool

# Send a message
curl -s -X POST http://localhost:8081 \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "method": "message/send",
    "params": {
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "What AI sessions are available?"}]
      }
    },
    "id": "1"
  }' | python3 -m json.tool
```

The AgentCard should have the same skills as the Quarkus version. The response format is identical — only the port differs.

## Step-by-Step Guide

See [../../docs/exercise-2.md](../../docs/exercise-2.md) for the full walkthrough.
