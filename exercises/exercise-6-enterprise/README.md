# Exercise 6: Enterprise A2A across two WildFly nodes

Run one agent on two WildFly instances sharing PostgreSQL and Kafka. The client
creates a task on Node A, subscribes to it on Node B, and sends the work to Node A.
Node B streams every resulting state change and artifact to the client via Kafka
replication. Finally, the client retrieves the completed task from both nodes
and checks that its status and all artifacts match. The database keeps tasks available
across server restarts.

## Prerequisites

- JDK 21+ and Maven 3.9+; set `JAVA_HOME` to the JDK you want WildFly to use.
- Podman with `podman-compose`, or Docker Compose.
- Free ports: 5432 and 9092 for infrastructure, 8080 and 9080 for HTTP,
  and 9990 and 10990 for WildFly management. gRPC also uses 9555 and 10555.

## Project layout

```text
exercise-6-enterprise/
├── pom.xml
├── podman-compose.yml
├── client/                 Java cross-node demo
└── server/
    ├── pom.xml             Shared provisioning configuration
    ├── common/             Agent classes and resources; builds ROOT.war
    ├── node-a/             Provisions the first WildFly instance
    └── node-b/             Provisions the second WildFly instance
```

Both nodes deploy the same `server/common/target/ROOT.war`. Their server
directories are separate: `server/node-a/target/wildfly` and
`server/node-b/target/wildfly`. The node modules have no agent sources.

## Key Files

The shared Java classes are under `server/common/src/main/java/dev/devconf/enterprise/`.
The shared configuration files are under `server/common/src/main/resources/META-INF/`.

| File | Purpose |
| --- | --- |
| [EnterpriseAgentExecutorProducer.java](server/common/src/main/java/dev/devconf/enterprise/EnterpriseAgentExecutorProducer.java) | Application-scoped executor; creates the task with the `init` handshake, emits three artifacts with pauses, and completes the task |
| [EnterpriseAgentCardProducer.java](server/common/src/main/java/dev/devconf/enterprise/EnterpriseAgentCardProducer.java) | Advertises the available transports and the serving node's ports |
| [EnterpriseClient.java](client/src/main/java/dev/devconf/enterprise/client/EnterpriseClient.java) | Creates and executes the task on Node A, prints streaming events from Node B until COMPLETED, then verifies the stored task on both nodes |
| [microprofile-config.properties](server/common/src/main/resources/META-INF/microprofile-config.properties) | Kafka broker, replication topic, and incoming/outgoing channels; each node overrides the consumer group at startup |
| [persistence.xml](server/common/src/main/resources/META-INF/persistence.xml) | Shared JPA persistence unit for tasks and push-notification configurations; updates the schema without recreating it on deployment |
| [server/pom.xml](server/pom.xml) | Shared WildFly provisioning, PostgreSQL datasource, and transport profiles |
| [server/common/pom.xml](server/common/pom.xml) | Packages the shared agent and SDK dependencies into `ROOT.war` |
| [server/node-a/pom.xml](server/node-a/pom.xml), [server/node-b/pom.xml](server/node-b/pom.xml) | Provision separate WildFly instances that deploy the common WAR |
| [podman-compose.yml](podman-compose.yml) | PostgreSQL and Kafka backing services, including the persistent database volume |

## Start infrastructure

From the exercise directory:

```bash
podman-compose up -d
# Docker alternative:
# docker compose -f podman-compose.yml up -d
```

This starts PostgreSQL 17 on port 5432 and Kafka 4.1.0 on port 9092. Wait for
PostgreSQL to accept connections and Kafka to start before starting the nodes.
The PostgreSQL volume preserves tasks when containers are stopped.

## Build both servers

From the exercise directory:

```bash
cd server
mvn clean package -Pjsonrpc
```

Choose exactly one server profile:

| Transport | Server build (from `server/`) | Client profile |
| --- | --- | --- |
| JSON-RPC | `mvn clean package -Pjsonrpc` | `run-jsonrpc` |
| REST | `mvn clean package -Prest` | `run-rest` |
| gRPC | `mvn clean package -Pgrpc` | `run-grpc` |

Stop both nodes before rebuilding. Always use `clean package` so changes to the
agent or transport profile reach the provisioned servers. `package` alone skips
provisioning when a server already exists.

## Start the nodes

In two terminals, starting from the exercise directory:

**Node A:**

```bash
cd server/node-a
POSTGRESQL_USER=devconf \
POSTGRESQL_PASSWORD=devconf \
POSTGRESQL_DATABASE=devconf \
./target/wildfly/bin/standalone.sh \
  -Djboss.tx.node.id=node-a \
  -Dmp.messaging.incoming.replicated-events-in.group.id=node-a
```

**Node B:**

```bash
cd server/node-b
POSTGRESQL_USER=devconf \
POSTGRESQL_PASSWORD=devconf \
POSTGRESQL_DATABASE=devconf \
./target/wildfly/bin/standalone.sh \
  -Djboss.socket.binding.port-offset=1000 \
  -Djboss.tx.node.id=node-b \
  -Dmp.messaging.incoming.replicated-events-in.group.id=node-b
```

**For gRPC, append `--stability=preview` to both startup commands.** The gRPC
profile provisions preview features; WildFly must also enable them at runtime.

The Kafka consumer groups must differ so both nodes receive every task event.
The transaction node IDs must also differ so recovery can identify each server.
The port offset makes Node B's AgentCard advertise its own HTTP and gRPC ports.

Check that both servers have deployed `ROOT.war` and serve their AgentCards:

```bash
curl -fsS http://localhost:8080/.well-known/agent-card.json -H 'A2A-Version: 1.0'
curl -fsS http://localhost:9080/.well-known/agent-card.json -H 'A2A-Version: 1.0'
```

## Run the cross-node client

From the exercise directory, choose the client profile matching the server build:

```bash
mvn compile exec:java -Prun-jsonrpc -pl client
# REST:
# mvn compile exec:java -Prun-rest -pl client
# gRPC:
# mvn compile exec:java -Prun-grpc -pl client
```

The command compiles the client before running it. It greets Maya by default;
use `-Dattendee.name=YourName` to change the name.

The client prints elapsed times, node names, task IDs, status changes, and every
artifact as it arrives. The executor works for about four seconds: after an
initial two-second pause it emits a greeting, then a progress artifact and a
summary, with one second between artifacts. The subscription waits for COMPLETED
before checking the stored task.

Expected output excerpt (times and IDs vary):

```text
[+1.000s] Node A (:8080) Created task <task-id> state=TASK_STATE_SUBMITTED
[+1.100s] Node B (:9080) Task <task-id> status=TASK_STATE_SUBMITTED
[+2.200s] Node B (:9080) Task <task-id> status=TASK_STATE_WORKING
[+4.200s] Node B (:9080) Task <task-id> artifact=greeting id=<artifact-id> text=Hello Maya
[+5.200s] Node B (:9080) Task <task-id> artifact=progress id=<artifact-id> text=Preparing your workshop welcome, Maya
[+6.200s] Node B (:9080) Task <task-id> artifact=summary id=<artifact-id> text=Welcome to the enterprise A2A demo, Maya!
[+6.200s] Node B (:9080) Task <task-id> status=TASK_STATE_COMPLETED
[+6.200s] Node B (:9080) Completed stream for task <task-id> with 3 artifacts
[+6.300s] Node A (:8080) GetTask: <task-id> state=TASK_STATE_COMPLETED artifacts=3 (all artifact IDs, names, and contents match the stream)
[+6.400s] Node B (:9080) GetTask: <task-id> state=TASK_STATE_COMPLETED artifacts=3 (all artifact IDs, names, and contents match the stream)
Agent responds:
Hello Maya
Preparing your workshop welcome, Maya
Welcome to the enterprise A2A demo, Maya!
```

`SUBMITTED` is the initial task state; `WORKING` and `COMPLETED` are delivered
while Node A executes the continuation. A snapshot and a replicated update can
print the same status more than once; each received event is shown. Node B loads the initial task from the
shared JPA task store and relays subsequent live events through Kafka. After
closing the streaming client, two fresh clients call `GetTask` on Node A and
Node B. Each lookup must return the original task ID, COMPLETED status, and the
same artifact IDs, names, and contents for all three artifacts; the demo fails
if either result differs. This confirms
that both nodes can retrieve the completed task from the shared TaskStore.

The `init` message ID is a demo convention: the executor creates the task and
leaves it open, giving the client time to subscribe on Node B before work starts.
The executor producer is application scoped and shared within each node.

To check persistence across restarts, replace `<task-id>` with the ID printed
by the client and query both nodes:

```bash
for port in 8080 9080; do
  curl -fsS "http://localhost:$port/" \
    -H 'A2A-Version: 1.0' \
    -H 'Content-Type: application/json' \
    --data '{"jsonrpc":"2.0","id":"check","method":"GetTask","params":{"id":"<task-id>"}}'
done
```

Stop and restart both servers using their original startup commands, then repeat
the request. It should return the same completed task and all three artifacts. JSON-RPC is
available alongside REST and gRPC, so this check works with every server profile.

## Stop the example

Use Ctrl+C in each server terminal, then from the exercise directory:

```bash
podman-compose stop
# Docker alternative:
# docker compose -f podman-compose.yml stop
```

Stopping preserves the database volume. Restarting the servers preserves
completed tasks, which either node can retrieve with `GetTask`.

## Full Instructions

See [../../docs/exercise-6.md](../../docs/exercise-6.md) for the complete
step-by-step guide. The same exercise is also covered in the
[HTML workshop walkthrough](../../workshop/index.html#ex6-intro).
