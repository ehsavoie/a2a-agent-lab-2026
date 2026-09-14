# Building the DevConf Concierge — A Multi-Agent A2A Ecosystem

A 2-hour hands-on lab that takes you from zero to a fully orchestrated, multi-runtime, multi-language, observable agent mesh using the **A2A protocol**, **LangChain4j**, and the **A2A Java SDK**.

## The Story

DevConf 2026 is next week and 5,000 attendees need help navigating the conference. You're building the **AI-powered Concierge** — a mesh of specialized agents that collaborate via A2A to answer any attendee question, from session recommendations to restaurant tips.

Each exercise adds a new agent or capability. By the end, you'll have a complete working system with:
- A **Session Agent** that recommends conference talks
- The same agent running on **three different Java runtimes**
- A **Python Travel Tips Agent** proving cross-language interop
- A **Concierge Orchestrator** that decomposes complex queries and dispatches them across the mesh
- **OpenTelemetry tracing** visualized in Jaeger across the entire call chain

## Architecture

```
                          ┌─────────────────────┐
                          │   Concierge Agent    │
                          │   (Orchestrator)     │
                          │   Quarkus :8090      │
                          └──────────┬───────────┘
                                     │
                    ┌────────────────┼────────────────┐
                    │                │                 │
           ┌───────▼──────┐  ┌──────▼───────┐  ┌─────▼──────────┐
           │ Session Agent │  │ Session Agent │  │ Travel Tips    │
           │ Quarkus :8080 │  │ Spring :8081  │  │ Agent (Python) │
           │               │  │ WildFly:8082  │  │ :9000          │
           └───────┬───────┘  └──────┬────────┘  └──────┬─────────┘
                   │                 │                   │
                   └─────────────────┼───────────────────┘
                                     │
                              ┌──────▼──────┐
                              │   Ollama    │
                              │   :11434    │
                              └─────────────┘
```

## Prerequisites

- **JDK 21+** (e.g., Temurin, GraalVM)
- **Maven 3.9+**
- **Python 3.11+** with `pip` or `uv`
- **Podman** with `podman-compose`
- **curl** or **httpie** for testing
- A terminal with at least 4 tabs/panes

## Quick Start

```bash
# 1. Clone the repo
git clone <repo-url>
cd a2a-agent-lab-2026

# 2. Start infrastructure (Ollama + Jaeger)
podman-compose up -d

# 3. Wait for Ollama to be ready and the model to be pulled
podman logs -f devconf-ollama-pull

# 4. Verify
curl http://localhost:11434/api/tags          # Should list 'mistral'
open http://localhost:16686                    # Jaeger UI
```

## Exercises

| # | Exercise | Time | What You Build |
|---|----------|------|----------------|
| 1 | [Your First A2A Agent](docs/exercise-1.md) | 30 min | Quarkus + LangChain4j Session Agent |
| 2 | [Multi-Runtime Deployment](docs/exercise-2.md) | 20 min | Same agent on Spring Boot + WildFly |
| 3 | [Cross-Language Interop](docs/exercise-3.md) | 15 min | Python Travel Tips Agent |
| 4 | [The Concierge Orchestrator](docs/exercise-4.md) | 25 min | Dynamic router across the mesh |
| 5 | [Enterprise Observability](docs/exercise-5.md) | 20 min | OpenTelemetry + Jaeger tracing |

Each exercise builds on the previous one. If you fall behind, check the `solutions/` directory for complete working code at each checkpoint.

## Building

All Java exercises can be compiled at once from the root:

```bash
mvn compile
```

Or from the exercises directory:

```bash
cd exercises
mvn compile
```

## Key Technologies

- **[A2A Protocol](https://google.github.io/A2A/)** — Open standard for agent-to-agent communication
- **[A2A Java SDK](https://github.com/a2aproject/a2a-java-sdk)** — Java implementation of the A2A protocol
- **[LangChain4j](https://docs.langchain4j.dev/)** — Java framework for LLM-powered applications
- **[A2A Jakarta EE SDK](https://github.com/wildfly-extras/a2a-jakarta)** — A2A integration for Jakarta EE / WildFly
- **[Quarkus](https://quarkus.io/)** — Supersonic Subatomic Java framework
- **[Spring Boot](https://spring.io/projects/spring-boot)** — Java application framework
- **[WildFly](https://www.wildfly.org/)** — Jakarta EE application server
- **[Ollama](https://ollama.ai/)** — Local LLM runner
- **[OpenTelemetry](https://opentelemetry.io/)** — Observability framework
- **[Jaeger](https://www.jaegertracing.io/)** — Distributed tracing backend
