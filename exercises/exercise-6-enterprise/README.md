# Exercise 6 — Enterprise A2A: Multi-Node WildFly with JPA & Kafka (Bonus)

> *"The DevSphere system needs to survive a node failure and scale horizontally. Deploy the same agent WAR on two WildFly nodes sharing a PostgreSQL database and a Kafka broker, then prove that a task created and executed on Node A can be observed live on Node B through Kafka replication."*

This bonus exercise shows how the A2A Java SDK's in-memory implementations (`InMemoryTaskStore`, `InMemoryQueueManager`) can be replaced with enterprise-grade JPA and Kafka implementations — with no changes to agent logic.

## Architecture

```
              +------------------------------+
              |     SHARED INFRASTRUCTURE    |
              |                              |
              |  PostgreSQL :5432            |
              |  - JPA TaskStore             |
              |  - JPA PNConfigStore         |
              |                              |
              |  Kafka :9092                 |
              |  - replicated-events topic   |
              +------------------------------+
                    ^               ^
                    |               |
  +-----------+     |               |     +-----------+
  |  Node A   |-----+               +-----|  Node B   |
  |   :8080   |                          |   :9080   |
  +-----------+                          +-----------+
       ^                                       |
       | 1. create task                        | 4. WORKING → COMPLETED
       |                                       v
  EnterpriseClient                      EnterpriseClient
  (sends to Node A)                     (subscribed to Node B)
```

## Enterprise Components

| Component | Replaces | Purpose |
|---|---|---|
| **JPA-backed TaskStore** | `InMemoryTaskStore` | Tasks survive restarts and are shared across nodes via PostgreSQL |
| **Kafka-replicated QueueManager** | `InMemoryQueueManager` | Every task state-change event is published to a Kafka topic and broadcast to all nodes |
| **JPA PushNotificationConfigStore** | In-memory | Stores push-notification subscriptions in PostgreSQL |

## The Agent: Conference Feedback

The agent in this exercise collects attendee feedback for conference sessions and provides per-talk or global summaries to speakers.

| Skill ID | Description |
|---|---|
| `submit-feedback` | Record a rating and comment for a speaker's session |
| `feedback-summary` | Return aggregated feedback for a speaker, optionally per session |

## Step 1 — Start the Infrastructure

```bash
cd exercises/exercise-6-enterprise
podman-compose up -d
# or: docker compose -f podman-compose.yml up -d
```

| Service | Port | Purpose |
|---|---|---|
| **PostgreSQL 17** | 5432 | JPA store: A2A tasks, push-notification configs, and `session_feedback` (seeded from `initdb/01-feedback.sql`) |
| **Kafka 4.1** | 9092 | Broadcast task-state events across nodes (`replicated-events` topic) |

> **If "No feedback found" on summary:** the `initdb/01-feedback.sql` seed only runs when the PostgreSQL volume is first created. If the `pgdata` volume already existed, recreate it: `podman-compose stop && podman-compose down -v && podman-compose up -d`

> **Stopping containers properly:** Always stop before removing — `podman-compose stop` then `podman-compose down`. To also wipe the PostgreSQL volume: `podman-compose stop && podman-compose down -v`

## Step 2 — Explore the AgentCard & AgentExecutor

Open `server/common/src/.../EnterpriseAgentCardProducer.java`. The AgentCard producer reads the WildFly socket-binding port-offset at runtime so the advertised URL is always correct — whether this request was served by Node A (offset 0) or Node B (offset 1000).

Open `EnterpriseAgentExecutorProducer.java` and study the **init handshake** pattern:

```java
@Override
public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
    boolean isNewTask = context.getTask() == null;
    if (isNewTask) {
        emitter.submit();  // persist task in JPA store, publish SUBMITTED event to Kafka
    }

    // Special convention: messageId "init" creates the task and returns immediately.
    // The client can then resubscribe via a *different node* before sending the real work.
    boolean isInitHandshake = isNewTask && "init".equals(context.getMessage().messageId());
    if (isInitHandshake) {
        return;  // task is SUBMITTED, open for continuation
    }

    emitter.startWork();         // publish WORKING event → broadcast to all nodes
    // ... process feedback ...
    emitter.complete();          // publish COMPLETED + artifact → Node B receives it via Kafka
}
```

The init handshake proves cross-node replication: the `init` message creates the task on Node A, then the client resubscribes via Node B. When Node A sends the `WORKING → COMPLETED` transition, those events travel through Kafka and arrive at Node B's SSE stream.

## Step 3 — Build & Run Node A and Node B

```bash
# Build the shared WAR and provision both server directories
cd exercises/exercise-6-enterprise/server
mvn clean package -Pjsonrpc
```

> Use `-Pjsonrpc` for JSON-RPC (recommended), `-Prest` for REST, or `-Pgrpc` for gRPC. Always use `mvn clean package` — a plain `package` skips provisioning when a server already exists.

**Start Node A (port 8080) — new terminal:**
```bash
cd exercises/exercise-6-enterprise/server/node-a
POSTGRESQL_USER=devconf \
POSTGRESQL_PASSWORD=devconf \
POSTGRESQL_DATABASE=devconf \
./target/wildfly/bin/standalone.sh \
  -Djboss.tx.node.id=node-a \
  -Dmp.messaging.incoming.replicated-events-in.group.id=node-a
```

**Start Node B (port 9080) — new terminal:**
```bash
cd exercises/exercise-6-enterprise/server/node-b
POSTGRESQL_USER=devconf \
POSTGRESQL_PASSWORD=devconf \
POSTGRESQL_DATABASE=devconf \
./target/wildfly/bin/standalone.sh \
  -Djboss.socket.binding.port-offset=1000 \
  -Djboss.tx.node.id=node-b \
  -Dmp.messaging.incoming.replicated-events-in.group.id=node-b
```

> **Unique consumer group IDs are mandatory.** Without this, Kafka distributes partitions between nodes and each node only receives events from its assigned partitions — breaking cross-node replication. The `jboss.tx.node.id` values must also differ for transaction recovery.

> **For gRPC:** append `--stability=preview` to both startup commands.

**Verify both nodes are up:**
```bash
curl -s http://localhost:8080/.well-known/agent-card.json \
  -H "A2A-Version: 1.0" | jq '{name, skills: [.skills[].id]}'

curl -s http://localhost:9080/.well-known/agent-card.json \
  -H "A2A-Version: 1.0" | jq '{name, skills: [.skills[].id]}'
```

## Step 4 — Run the Cross-Node Demo

The `client/` module contains `EnterpriseClient`, which orchestrates the full cross-node scenario.

```bash
cd exercises/exercise-6-enterprise

# Submit feedback via JSON-RPC
mvn compile exec:java -Pjsonrpc -pl client \
  -Dmessage="Feedback for Mario Fusco, session: Building Production-Ready Agentic Systems with LangChain4j and Quarkus, rating: 5, absolutely loved the live coding demo!"

# Request a summary via JSON-RPC
mvn compile exec:java -Pjsonrpc -pl client \
  -Dmessage="Summary for Mario Fusco"
```

The cross-node sequence:
```
1. Node A (:8080)   ← sendMessage(messageId="init")
                         Task created, state: SUBMITTED
                         Task ID returned to client

2. Node B (:9080)   ← subscribeToTask(taskId)
                         Node B loads the task from shared JPA TaskStore
                         (it was created on Node A — shared PostgreSQL makes it visible)

3. Node A (:8080)   ← sendMessage(taskId=..., "Feedback for Mario Fusco, ...")
                         Task transitions: SUBMITTED → WORKING

4. Node B (:9080)   receives via Kafka:
                         WORKING event  → SSE stream
                         COMPLETED event + feedback confirmation artifact
```

Expected output:
```
[+0.9s] Node A (:8080) Sending init message
[+1.3s] Node A (:8080) Created task a1b2c3d4-... state=TASK_STATE_SUBMITTED
[+1.3s] Node B (:9080) Subscribing to task a1b2c3d4-...
[+1.4s] Node B (:9080) Task a1b2c3d4-... status=TASK_STATE_SUBMITTED
[+2.3s] Node A (:8080) Sending continuation text=Feedback for Mario Fusco...
[+2.6s] Node B (:9080) Task a1b2c3d4-... status=TASK_STATE_WORKING
[+4.6s] Node B (:9080) Task a1b2c3d4-... artifact=response text=Thank you! Feedback recorded...
[+4.6s] Node B (:9080) Task a1b2c3d4-... status=TASK_STATE_COMPLETED
Agent responds:
Thank you! Feedback recorded for Mario Fusco — Building Production-Ready Agentic Systems with LangChain4j and Quarkus
★★★★★  "absolutely loved the live coding demo!"
```

## Checkpoint

- [ ] PostgreSQL and Kafka running via `podman-compose up -d` (seed feedback loaded from `initdb/01-feedback.sql`)
- [ ] Node A on port 8080, Node B on port 9080 — both serving the Conference Feedback Agent AgentCard
- [ ] JPA-backed task store: tasks persist across restarts and are visible from both nodes
- [ ] Kafka-replicated queue manager: `WORKING → COMPLETED` events broadcast across nodes
- [ ] Feedback submitted via Node A is included in summaries returned by Node B (shared PostgreSQL)
- [ ] Task created on Node A is subscribed and observed on Node B, proving cross-node task replication

## Project Structure

```
exercise-6-enterprise/
├── server/
│   ├── common/          # Shared agent WAR sources (deployed to both nodes)
│   ├── node-a/          # WildFly provisioned for Node A
│   └── node-b/          # WildFly provisioned for Node B
├── client/              # EnterpriseClient (cross-node demo runner)
├── initdb/
│   └── 01-feedback.sql  # PostgreSQL seed data for the feedback demo
└── podman-compose.yml   # PostgreSQL + Kafka
```
