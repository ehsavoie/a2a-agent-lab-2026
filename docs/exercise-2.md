# Exercise 2: Multi-Runtime — Same Contract, Three Servers

**Time:** 20 minutes

> _"The conference IT team runs Spring Boot in production, the innovation lab uses Quarkus, and the data team just wants a lightweight process. Can your Session Agent run everywhere? With A2A, the answer is yes — the AgentCard contract is the interface, not the framework."_

## Overview

In this exercise you will deploy the **exact same agent capability** on three different Java runtimes:

| Runtime | Framework | DI Style | Port |
|---------|-----------|----------|------|
| Quarkus | Quarkus + CDI | `@ApplicationScoped`, `@Produces` | 8080 |
| Spring Boot | Spring Boot + Spring DI | `@Component`, `@Bean` | 8081 |
| WildFly | Jakarta EE + CDI | `@ApplicationScoped`, `@Produces` (WAR) | 8082 |

All three will serve **identical AgentCards** with the **same skills**. Any A2A client that works with one will work with all three without code changes.

### What you will learn

- The AgentCard is the **contract** — clients never need to know what framework runs behind it
- The same A2A SDK works with CDI, Spring, or plain Java
- Different runtimes require different wiring but produce identical protocol behavior

---

## Key Insight

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Quarkus   │     │ Spring Boot │     │   WildFly   │
│    :8080    │     │    :8081    │     │    :8082    │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                  ┌────────▼────────┐
                  │  Same AgentCard │
                  │  Same Skills    │
                  │  Same Protocol  │
                  └─────────────────┘
```

An A2A client only needs the agent's URL. It fetches the AgentCard, reads the skills, sends messages. The runtime behind the URL is invisible.

---

## Step 1: Spring Boot Version

Open the project at `exercises/exercise-2a-spring-boot/`. This is the same Session Agent, but wired with Spring.

### Key Differences from Quarkus

| Aspect | Quarkus (Exercise 1) | Spring Boot (Exercise 2a) |
|--------|---------------------|--------------------------|
| Bean registration | `@ApplicationScoped` | `@Component` |
| Producer method | `@Produces` | `@Bean` in `@Configuration` |
| AI Service creation | `@RegisterAiService` (declarative) | `AiServices.builder()` (programmatic) |
| Config injection | `@ConfigProperty` | `@Value` |

### SessionTool — `@Component` instead of `@ApplicationScoped`

The tool logic is identical. Only the CDI annotations change:

```java
@Component  // was @ApplicationScoped
public class SessionTool {

    @Value("${session.data.path}")  // was @ConfigProperty
    private String sessionDataPath;

    @PostConstruct
    void loadSessions() { /* same logic */ }

    @Tool("Search conference sessions...")
    public String searchSessions(String query) { /* same logic */ }
}
```

### SessionService — Programmatic wiring

In Quarkus, `@RegisterAiService` creates the proxy automatically. Spring Boot doesn't have this, so we wire it explicitly:

```java
// SessionService.java — same interface, no @RegisterAiService
public interface SessionService {
    @SystemMessage("You are the DevConf 2026 Session Recommender...")
    String chat(@UserMessage String userMessage);
}

// SessionAgentConfig.java — wire it programmatically
@Configuration
public class SessionAgentConfig {

    @Bean
    public SessionService sessionService(
            ChatLanguageModel chatLanguageModel, SessionTool sessionTool) {
        return AiServices.builder(SessionService.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(sessionTool)
                .build();
    }
}
```

### AgentCard and AgentExecutor — `@Bean` instead of `@Produces`

The `@Configuration` class also produces the AgentCard and AgentExecutor as Spring beans:

```java
@Bean
public AgentCard agentCard() {
    return AgentCard.builder()
            .name(agentName)
            // ... same builder chain as Exercise 1 ...
            .build();
}

@Bean
public AgentExecutor agentExecutor(SessionService sessionService) {
    return new AgentExecutor() {
        @Override
        public void execute(RequestContext context, AgentEmitter emitter) {
            // ... same logic as Exercise 1 ...
        }
    };
}
```

### Run on port 8081

The `application.properties` sets:

```properties
server.port=8081
a2a.agent.url=http://localhost:8081
```

Start it:

```bash
cd exercises/exercise-2a-spring-boot
mvn spring-boot:run
```

Test:

```bash
curl -s http://localhost:8081/.well-known/agent.json | jq .name
# → "Session Recommender Agent"
```

---

## Step 2: WildFly + A2A Jakarta EE Version

Open `exercises/exercise-2b-ja2a/`. This deploys the same Session Agent as a **standard Jakarta EE WAR** on **WildFly 40**, using the **A2A Jakarta EE SDK** (`a2a-jakarta-jsonrpc`).

### Key Differences from Quarkus

| Aspect | Quarkus (Exercise 1) | WildFly (Exercise 2b) |
|--------|---------------------|----------------------|
| Runtime | Quarkus dev mode | WildFly 40 (provisioned by Maven) |
| SDK | `a2a-java-sdk-reference-jsonrpc` | `a2a-jakarta-jsonrpc` + `a2a-java-sdk-server-common` |
| Packaging | JAR (Quarkus uber-jar) | WAR (deployed as ROOT.war) |
| LangChain4j | Quarkus extension (`quarkus-langchain4j-ollama`) | Plain LangChain4j library (`langchain4j-ollama`) |
| CDI | Quarkus Arc | Jakarta CDI (WildFly Weld) |
| AI Service creation | `@RegisterAiService` (declarative) | `AiServices.builder()` (programmatic) |
| Port | 8080 | 8082 (via port offset) |

### WAR Structure

The project uses **WAR packaging** with WildFly provisioned via the `wildfly-maven-plugin`:

```xml
<packaging>war</packaging>

<!-- A2A Jakarta EE transport -->
<dependency>
    <groupId>org.wildfly.a2a</groupId>
    <artifactId>a2a-jakarta-jsonrpc</artifactId>
</dependency>

<!-- WildFly provisioning -->
<plugin>
    <groupId>org.wildfly.plugins</groupId>
    <artifactId>wildfly-maven-plugin</artifactId>
    <configuration>
        <feature-packs>
            <feature-pack>
                <groupId>org.wildfly</groupId>
                <artifactId>wildfly-galleon-pack</artifactId>
                <version>40.0.0.Final</version>
            </feature-pack>
        </feature-packs>
        <name>ROOT.war</name>
    </configuration>
</plugin>
```

### JAX-RS Application

A minimal JAX-RS application class activates the REST endpoint:

```java
@ApplicationPath("/")
public class SessionAgentApplication extends Application {
}
```

### CDI Producers — Same Pattern, Different Runtime

The AgentCard and AgentExecutor producers use the **exact same CDI pattern** as Quarkus:

```java
@ApplicationScoped
public class SessionAgentCardProducer {

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name("Session Recommender Agent")
                // ... same builder chain as Exercise 1 ...
                .build();
    }
}
```

### LangChain4j — Manual Wiring

Without Quarkus's `@RegisterAiService`, the AI service is built programmatically in a CDI bean:

```java
@ApplicationScoped
public class SessionServiceProducer {

    private SessionService sessionService;

    @PostConstruct
    void init() {
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .baseUrl("http://localhost:11434")
                .modelName("mistral")
                .build();

        sessionService = AiServices.builder(SessionService.class)
                .chatLanguageModel(chatModel)
                .tools(new SessionTool())
                .build();
    }

    public SessionService getSessionService() {
        return sessionService;
    }
}
```

### Configuration

Two metadata files in `META-INF/`:
- `beans.xml` — enables CDI bean discovery
- `microprofile-config.properties` — sets `a2a.authorization.required=false`

### Build and Run

```bash
cd exercises/exercise-2b-ja2a

# Build the WAR and provision WildFly
mvn package -Dsession.data.path=$(pwd)/../../conference-data/sessions.json

# Start WildFly with port offset (HTTP on 8082)
./target/wildfly/bin/standalone.sh -Djboss.socket.binding.port-offset=2 \
  -Dsession.data.path=$(pwd)/../../conference-data/sessions.json
```

Test:

```bash
curl -s http://localhost:8082/.well-known/agent.json | jq .name
# → "Session Recommender Agent"
```

> **Note:** The `wildfly-maven-plugin` provisions a complete WildFly server in `target/wildfly/` with just the layers needed to run your WAR. The `ROOT.war` deployment name means the agent is accessible at the root context path.

---

## Step 3: Cross-Runtime Verification

With all three agents running simultaneously on ports 8080, 8081, 8082, let's prove they are interchangeable.

### Compare AgentCards

```bash
# Fetch all three AgentCards and compare skills
for port in 8080 8081 8082; do
  echo "=== Port $port ==="
  curl -s http://localhost:$port/.well-known/agent.json \
    | jq '{name, skills: [.skills[].id]}'
  echo
done
```

You should see the **same skills** from all three:

```json
{
  "name": "Session Recommender Agent",
  "skills": ["session-search", "session-recommend"]
}
```

### Send the Same Query to All Three

```bash
MESSAGE='{"jsonrpc":"2.0","method":"message/send","params":{"message":{"role":"user","parts":[{"type":"text","text":"What AI sessions are available?"}]}},"id":"1"}'

for port in 8080 8081 8082; do
  echo "=== Port $port ==="
  curl -s -X POST http://localhost:$port/a2a \
    -H "Content-Type: application/json" \
    -d "$MESSAGE" | jq .result.status.state
  echo
done
```

All three should return `"completed"`.

---

## Step 4: Write an A2A Client

To really prove interoperability, write a simple Java client that calls all three agents with the same code — only the URL changes.

Create a small test class or Quarkus `@QuarkusMain`:

```java
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.TextPart;

// Pseudo-code — adapt to the actual client API
var urls = List.of(
    "http://localhost:8080",
    "http://localhost:8081",
    "http://localhost:8082"
);

for (String url : urls) {
    // Same client, same code — only the URL differs
    A2AClient client = A2AClient.builder().url(url).build();

    AgentCard card = client.getAgentCard();
    System.out.println("Agent: " + card.name() + " at " + url);
    System.out.println("Skills: " + card.skills().stream()
        .map(s -> s.name()).toList());

    // Send a message
    Task task = client.sendMessage("What sessions are about AI?");
    System.out.println("Status: " + task.status().state());
    System.out.println("Response: " +
        task.artifacts().get(0).parts().get(0));
    System.out.println("---");
}
```

The output will show three identical sets of skills and three responses — from three completely different runtimes.

---

## Checkpoint

At this point you should have:

- [x] **Three agents** running simultaneously on ports 8080, 8081, 8082
- [x] All three serving **identical AgentCards** with the same skills
- [x] All three responding to the **same `message/send` request** with equivalent answers
- [x] The realization that **A2A decouples the interface from the implementation**

**The AgentCard is the contract.** A client that can talk to one A2A agent can talk to any A2A agent, regardless of the language, framework, or runtime behind it. In the next exercise, you will prove this across *languages* by adding a Python agent to your mesh.

---

> **Discussion: When would you use each runtime?**
>
> - **Quarkus** — When you want the fastest startup time, native image compilation, and a rich CDI-based extension ecosystem
> - **Spring Boot** — When your team already uses Spring and you want seamless integration with the Spring ecosystem
> - **WildFly** — When your enterprise runs Jakarta EE application servers and you want standard WAR deployment with full Jakarta EE services (JPA, JMS, EJB, etc.) available
>
> The beauty of A2A: the choice is purely an implementation detail. Your agents are interchangeable.
