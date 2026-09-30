# Exercise 6: Enterprise A2A — Multi-Node WildFly with JPA & Kafka

**Bonus exercise**

> _A task is created and executed on Node A. A client connected to Node B
> watches its progress and artifacts arrive live. After completion, either
> node can retrieve the task, even after both servers restart._

## Overview

In this exercise you will run the same A2A agent on two WildFly instances and
explore how shared persistence and event replication support multiple nodes:

1. Store tasks in PostgreSQL using the JPA-backed TaskStore.
2. Broadcast task events between nodes through Kafka.
3. Create and execute a task on Node A while subscribing through Node B.
4. Print every received status and artifact, waiting for COMPLETED.
5. Retrieve the completed task from both nodes and check persistence after restart.

The example greets an attendee and produces three artifacts over about four
seconds. Its predictable output makes the task lifecycle easy to follow.

## Architecture

```text
EnterpriseClient
  ├─ Create task and send continuation ──> Node A (:8080)
  ├─ Subscribe to task ─────────────────> Node B (:9080)
  └─ Final GetTask calls ────────────────> Both nodes

Node A ───────> PostgreSQL (:5432) <─────── Node B
                  Shared JPA stores

Node A ───────> Kafka (:9092) ────────────> Node B
               replicated-events topic      │
                                            └─ Live events to subscribed client
```

PostgreSQL holds the task state and artifacts so either node can retrieve them.
Kafka carries live events so a subscription on Node B can observe work on Node A.
Both nodes consume the replication topic using separate consumer groups.

The deployment also includes a JPA-backed PushNotificationConfigStore. This demo
uses task streaming; it does not send push notifications.

## Prerequisites

- JDK 21+ and Maven 3.9+; set `JAVA_HOME` to the JDK used to run WildFly.
- Podman with `podman-compose`, or Docker Compose.
- Available ports: PostgreSQL 5432, Kafka 9092, HTTP 8080/9080, and WildFly
  management 9990/10990. The gRPC profile also uses 9555/10555.

This greeting example requires no LLM or API key. Its console output provides
the demonstration's event trace; OpenTelemetry is intentionally omitted.

Stop other workshop services using these ports before starting this exercise.
Start each command block below from the repository root, in a new terminal or
after returning to the root.

## Step 1: Explore the Shared Server Project

```text
exercises/exercise-6-enterprise/
├── pom.xml
├── podman-compose.yml
├── client/
│   └── src/main/java/dev/devconf/enterprise/client/EnterpriseClient.java
└── server/
    ├── pom.xml
    ├── common/
    │   └── src/main/
    │       ├── java/dev/devconf/enterprise/
    │       │   ├── EnterpriseAgentApplication.java
    │       │   ├── EnterpriseAgentCardProducer.java
    │       │   └── EnterpriseAgentExecutorProducer.java
    │       └── resources/META-INF/
    │           ├── beans.xml
    │           ├── microprofile-config.properties
    │           └── persistence.xml
    ├── node-a/pom.xml
    └── node-b/pom.xml
```

`server/common` builds one `ROOT.war`. The node modules provision separate
WildFly instances and deploy that same WAR; they have no agent sources.
`server/pom.xml` shares the provisioning configuration, PostgreSQL datasource,
and transport profiles between the nodes.

The enterprise dependencies in `server/common/pom.xml` provide:

| Dependency | Purpose |
| --- | --- |
| `a2a-java-extras-task-store-database-jpa` | JPA-backed TaskStore |
| `a2a-java-extras-push-notification-config-store-database-jpa` | JPA-backed push-notification configuration storage |
| `a2a-java-queue-manager-replicated-core` | Replicated queue manager |
| `a2a-java-queue-manager-replication-mp-reactive` | MicroProfile Reactive Messaging replication strategy using Kafka |
| `a2a-java-sdk-microprofile-config` | Reads the deployment's MicroProfile configuration |

The SDK selects the enterprise store and queue implementations through CDI
when these dependencies and their configuration are present.

## Step 2: Start PostgreSQL and Kafka

```bash
cd exercises/exercise-6-enterprise
podman-compose up -d
# Docker alternative:
# docker compose -f podman-compose.yml up -d
```

The compose file starts PostgreSQL 17 and Kafka 4.1.0. Wait for both services to
finish starting before starting WildFly. PostgreSQL uses the `devconf` database,
user, and password, and saves its data in the `pgdata` volume.

## Step 3: Explore Persistence and Replication Configuration

Open `server/common/src/main/resources/META-INF/persistence.xml`. The `a2a-java`
persistence unit uses `java:comp/DefaultDataSource`, configured for PostgreSQL by
the server build, and includes the SDK's task and push-notification entities.

Its schema setting is:

```xml
<property name="hibernate.hbm2ddl.auto" value="update"/>
```

This preserves existing tasks when a node starts or the agent is redeployed.
Both nodes connect to the same database.

Next, open `META-INF/microprofile-config.properties` in that resource directory.
The outgoing and incoming channels use the same Kafka topic:

```properties
mp.messaging.connector.smallrye-kafka.bootstrap.servers=localhost:9092

mp.messaging.outgoing.replicated-events-out.connector=smallrye-kafka
mp.messaging.outgoing.replicated-events-out.topic=replicated-events

mp.messaging.incoming.replicated-events-in.connector=smallrye-kafka
mp.messaging.incoming.replicated-events-in.topic=replicated-events
```

Each node must override `mp.messaging.incoming.replicated-events-in.group.id`
with a different value at startup. Separate groups allow both nodes to consume
every event. Sharing a group distributes partitions between the nodes and can
prevent the subscribing node from receiving another node's events.

## Step 4: Explore the AgentCard and Executor

### AgentCard: advertise the serving node

`EnterpriseAgentCardProducer` reads `jboss.socket.binding.port-offset` and
computes its advertised URLs. The same WAR therefore advertises port 8080 on
Node A and port 9080 on Node B. With the gRPC profile, its gRPC ports are 9555
and 10555. The card advertises streaming and the `hello_world` skill.

The transport profiles control which transports are packaged. JSON-RPC is
available with every profile; REST and gRPC add their respective transports.

### Executor: separate task creation from work

`EnterpriseAgentExecutorProducer` produces an application-scoped executor.
The executor uses a demo convention: a new message whose `messageId` is `init`
submits a task and returns, leaving the task open for continuation.

This gives the client time to subscribe through Node B before sending the real
work to Node A. The `init` message ID is specific to this demo.

Once the continuation arrives, the executor runs this sequence:

```text
startWork()           → WORKING
pause(2000)
addArtifact(...)      → greeting: "Hello Maya"
pause(1000)
addArtifact(...)      → progress: "Preparing your workshop welcome, Maya"
pause(1000)
addArtifact(...)      → summary: "Welcome to the enterprise A2A demo, Maya!"
complete()            → COMPLETED
```

The artifact and status events are published through Kafka and relayed to the
client subscribed on Node B. The pauses make the live updates visible.

## Step 5: Build and Start Both Nodes

Choose one transport profile and build from `server/`:

```bash
cd exercises/exercise-6-enterprise/server
mvn clean package -Pjsonrpc
```

| Transport | Server build (from `server/`) | Client profile |
| --- | --- | --- |
| JSON-RPC | `mvn clean package -Pjsonrpc` | `run-jsonrpc` |
| REST | `mvn clean package -Prest` | `run-rest` |
| gRPC | `mvn clean package -Pgrpc` | `run-grpc` |

Stop both nodes before rebuilding. Use `clean package` when changing agent code
or transport profiles: `package` alone skips provisioning an existing server.

### Node A

In a new terminal:

```bash
cd exercises/exercise-6-enterprise/server/node-a
POSTGRESQL_USER=devconf \
POSTGRESQL_PASSWORD=devconf \
POSTGRESQL_DATABASE=devconf \
./target/wildfly/bin/standalone.sh \
  -Djboss.tx.node.id=node-a \
  -Dmp.messaging.incoming.replicated-events-in.group.id=node-a
```

### Node B

In another terminal:

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

**For gRPC, append `--stability=preview` to both startup commands.** The profile
provisions preview features, which must also be enabled at runtime.

The transaction node IDs differ so transaction recovery can identify each
server. The Kafka groups differ so each node receives every replicated event.
Node B's port offset separates its HTTP, management, and gRPC ports from Node A.

Wait for both servers to deploy `ROOT.war` and join their Kafka consumer groups.
Check their AgentCards from another terminal:

```bash
curl -fsS http://localhost:8080/.well-known/agent-card.json -H 'A2A-Version: 1.0'
curl -fsS http://localhost:9080/.well-known/agent-card.json -H 'A2A-Version: 1.0'
```

Each card should advertise its own ports and the selected transport.

## Step 6: Run the Cross-Node Client

Choose the client profile matching the server build:

```bash
cd exercises/exercise-6-enterprise
mvn compile exec:java -Prun-jsonrpc -pl client
# REST:
# mvn compile exec:java -Prun-rest -pl client
# gRPC:
# mvn compile exec:java -Prun-grpc -pl client
```

The command compiles the client before running it. Maya is the default attendee;
add `-Dattendee.name=YourName` to use another name.

`EnterpriseClient.runCrossNodeDemo()` performs five steps:

1. Send `init` to Node A and retain the returned task ID.
2. Subscribe to that task through Node B.
3. Send the attendee's name as a continuation to Node A.
4. Print every received status and artifact on Node B, waiting for COMPLETED.
5. Close the streaming client, then use fresh clients to retrieve and verify
   the completed task from Node A and Node B.

The completion callback supplies the SDK's accumulated task, including all
three artifacts. An artifact event alone does not finish the wait. Failed final
states, subscription errors, or stream closure before COMPLETED fail the demo.

Expected output excerpt (times and IDs vary):

```text
[+0.527s] Node B (:9080) Task <task-id> status=TASK_STATE_SUBMITTED
[+1.346s] Node B (:9080) Task <task-id> status=TASK_STATE_SUBMITTED
[+1.375s] Node B (:9080) Task <task-id> status=TASK_STATE_WORKING
[+3.386s] Node B (:9080) Task <task-id> artifact=greeting id=<artifact-id> text=Hello Maya
[+4.398s] Node B (:9080) Task <task-id> artifact=progress id=<artifact-id> text=Preparing your workshop welcome, Maya
[+5.398s] Node B (:9080) Task <task-id> artifact=summary id=<artifact-id> text=Welcome to the enterprise A2A demo, Maya!
[+5.401s] Node B (:9080) Task <task-id> status=TASK_STATE_COMPLETED
[+5.402s] Node B (:9080) Completed stream for task <task-id> with 3 artifacts
[+5.420s] Node A (:8080) GetTask: <task-id> state=TASK_STATE_COMPLETED artifacts=3 (all artifact IDs, names, and contents match the stream)
[+5.428s] Node B (:9080) GetTask: <task-id> state=TASK_STATE_COMPLETED artifacts=3 (all artifact IDs, names, and contents match the stream)
Agent responds:
Hello Maya
Preparing your workshop welcome, Maya
Welcome to the enterprise A2A demo, Maya!
```

A snapshot and a replicated update can report the same status more than once;
the client prints each received event. Normal SSE completion is logged as
`Stream closed`.

Both final lookups must return the original task ID, COMPLETED status, and
matching artifact IDs, names, and contents. The client fails if either stored
task differs from the completed stream.

## Step 7: Verify Persistence After Restart

Copy the task ID printed by the client. Query both nodes from another terminal,
replacing `<task-id>` below with that ID:

```bash
TASK_ID='<task-id>'
for port in 8080 9080; do
  curl -fsS "http://localhost:$port/" \
    -H 'A2A-Version: 1.0' \
    -H 'Content-Type: application/json' \
    --data "{\"jsonrpc\":\"2.0\",\"id\":\"check\",\"method\":\"GetTask\",\"params\":{\"id\":\"$TASK_ID\"}}"
done
```

Both responses should contain the completed task and all three artifacts.
JSON-RPC remains available alongside REST and gRPC, so this check works with
every server profile.

Stop both servers with Ctrl+C, then restart them with the same commands from
Step 5, including their database environment variables and node-specific
properties. Repeat the lookup loop using the original task ID.

The task and all three artifacts should still be available from both nodes.
This demonstrates that the TaskStore is persisted in the shared database.

## Step 8: Stop the Example

Use Ctrl+C in both server terminals. Then stop the backing services:

```bash
cd exercises/exercise-6-enterprise
podman-compose stop
# Docker alternative:
# docker compose -f podman-compose.yml stop
```

Stopping the containers preserves the PostgreSQL volume and its saved tasks.

## Checkpoint

- Both nodes deploy the same shared agent WAR and advertise their own URLs.
- PostgreSQL stores tasks that either node can retrieve.
- Kafka delivers Node A's status and artifact updates to the Node B subscription.
- The client prints all three artifacts about one second apart and waits for COMPLETED.
- Fresh clients retrieve matching completed tasks and artifacts from both nodes.
- Both nodes can retrieve the original task after restarting.

See the [exercise README](../exercises/exercise-6-enterprise/README.md) for the
run commands and key files, or the
[HTML workshop section](../workshop/index.html#ex6-intro) for the presentation view.
