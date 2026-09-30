package dev.devconf.enterprise.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import io.grpc.ManagedChannelBuilder;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransport;
import org.a2aproject.sdk.client.transport.grpc.GrpcTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskArtifactUpdateEvent;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;

/**
 * Demonstrates cross-node A2A task replication:
 *  1. Creates a task via Node A (port 8080) using the "init" handshake
 *  2. Resubscribes to that task via Node B (port 9080), which has never seen it locally
 *  3. Sends the real work message back to Node A
 *  4. Observes the WORKING → COMPLETED transition on Node B, proving Kafka replication
 *  5. Retrieves the completed task from both nodes, demonstrating shared TaskStore access
 */
public class EnterpriseClient implements AutoCloseable {
    private static final int BASE_JSONRPC_PORT = 8080;
    private static final int NODE_A_PORT_OFFSET = 0;
    private static final int NODE_B_PORT_OFFSET = 1000;

    private final Client client;
    private final String nodeLabel;
    private final long startedAt;

    public EnterpriseClient(String protocol, int nodePortOffset) throws Exception {
        this(protocol, nodePortOffset, true, false);
    }

    public EnterpriseClient(String protocol, int nodePortOffset, boolean streaming, boolean polling) throws Exception {
        this(protocol, nodePortOffset, streaming, polling, System.nanoTime());
    }

    private EnterpriseClient(String protocol, int nodePortOffset, boolean streaming, boolean polling, long startedAt)
            throws Exception {
        this.startedAt = startedAt;
        String nodeName = nodePortOffset == NODE_A_PORT_OFFSET ? "Node A" : "Node B";
        nodeLabel = nodeName + " (:" + (BASE_JSONRPC_PORT + nodePortOffset) + ")";
        trace("Connecting using " + protocol);
        String cardBaseUrl = "http://localhost:" + (BASE_JSONRPC_PORT + nodePortOffset);
        AgentCard agentCard = A2ACardResolver.builder()
                .baseUrl(cardBaseUrl)
                .build()
                .getAgentCard();

        ClientConfig config = new ClientConfig.Builder()
                .setAcceptedOutputModes(List.of("text"))
                .setUseClientPreference(true)
                .setStreaming(streaming)
                .setPolling(polling)
                .build();

        ClientBuilder clientBuilder = Client.builder(agentCard).clientConfig(config);
        TransportProtocol prot = TransportProtocol.fromString(protocol);
        switch (prot) {
            case JSONRPC -> clientBuilder.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder());
            case HTTP_JSON -> clientBuilder.withTransport(RestTransport.class, new RestTransportConfigBuilder());
            case GRPC -> clientBuilder.withTransport(
                    GrpcTransport.class,
                    new GrpcTransportConfigBuilder().channelFactory(
                            target -> ManagedChannelBuilder.forTarget(target).usePlaintext().build()));
        }
        client = clientBuilder.build();
    }

    private void trace(String message) {
        double elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
        System.out.printf(Locale.ROOT, "[+%.3fs] %s %s%n", elapsedSeconds, nodeLabel, message);
    }

    public String sendInitMessage() throws Exception {
        Message initMessage = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId("init")
                .parts(List.of(new TextPart("init")))
                .build();

        CompletableFuture<String> taskId = new CompletableFuture<>();
        BiConsumer<ClientEvent, AgentCard> consumer = (event, agentCard) -> {
            if (event instanceof TaskEvent taskEvent) {
                trace("Created task " + taskEvent.getTask().id()
                        + " state=" + taskEvent.getTask().status().state());
                taskId.complete(taskEvent.getTask().id());
            } else {
                taskId.completeExceptionally(new IllegalStateException("Expected a TaskEvent"));
            }
        };
        trace("Sending init message");
        client.sendMessage(initMessage, Collections.singletonList(consumer), null, null);
        return taskId.get(10, TimeUnit.SECONDS);
    }

    public void sendContinuationMessage(String taskId, String message) throws Exception {
        Message continuation = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .taskId(taskId)
                .parts(List.of(new TextPart(message)))
                .build();
        trace("Sending continuation for task " + taskId + " text=" + message);
        client.sendMessage(continuation, Collections.emptyList(), null, null);
        trace("Continuation request returned for task " + taskId);
    }

    /**
     * Resubscribes to {@code taskId}, prints each status/artifact event as it arrives,
     * and returns the complete task only after a COMPLETED event. The call's blocking behaviour is
     * transport-dependent, so it runs on its own thread; synchronisation happens through the future.
     */
    public CompletableFuture<Task> resubscribeUntilCompleted(String taskId) {
        CompletableFuture<Task> completion = new CompletableFuture<>();

        BiConsumer<ClientEvent, AgentCard> consumer = (event, agentCard) -> {
            if (event instanceof TaskEvent snapshot) {
                Task task = snapshot.getTask();
                if (task.artifacts() != null) {
                    for (Artifact artifact : task.artifacts()) {
                        traceArtifact(task.id(), artifact);
                    }
                }
                recordStatus(task, task.status(), completion);
            } else if (event instanceof TaskUpdateEvent update) {
                if (update.getUpdateEvent() instanceof TaskStatusUpdateEvent statusUpdate) {
                    recordStatus(update.getTask(), statusUpdate.status(), completion);
                } else if (update.getUpdateEvent() instanceof TaskArtifactUpdateEvent artifactUpdate) {
                    traceArtifact(artifactUpdate.taskId(), artifactUpdate.artifact());
                }
            }
        };

        trace("Subscribing to task " + taskId);
        Thread subscriberThread = new Thread(() -> {
            try {
                client.subscribeToTask(new TaskIdParams(taskId), Collections.singletonList(consumer),
                        error -> {
                            // SSE uses a null error to signal normal stream completion.
                            if (error == null) {
                                trace("Stream closed for task " + taskId);
                                if (!completion.isDone()) {
                                    completion.completeExceptionally(new IllegalStateException(
                                            "Stream closed before COMPLETED for task " + taskId));
                                }
                            } else {
                                trace("Stream error for task " + taskId + ": " + error);
                                completion.completeExceptionally(error);
                            }
                        }, null);
            } catch (Exception e) {
                trace("Subscription failed for task " + taskId + ": " + e);
                completion.completeExceptionally(e);
            }
        }, "resubscribe-" + taskId);
        subscriberThread.setDaemon(true);
        subscriberThread.start();
        return completion;
    }

    private void recordStatus(Task task, TaskStatus status, CompletableFuture<Task> completion) {
        String message = status.message() == null ? "" : " message=" + status.message().parts();
        trace("Task " + task.id() + " status=" + status.state() + message);
        if (status.state() == TaskState.TASK_STATE_COMPLETED) {
            trace("Completed stream for task " + task.id() + " with " + artifactContents(task).size() + " artifacts");
            completion.complete(task);
        } else if (status.state().isFinal()) {
            completion.completeExceptionally(new IllegalStateException(
                    "Task " + task.id() + " ended with " + status.state() + message));
        }
    }

    private void traceArtifact(String taskId, Artifact artifact) {
        String text = String.join(" | ", artifact.parts().stream().map(EnterpriseClient::describePart).toList());
        trace("Task " + taskId + " artifact=" + artifact.name() + " id=" + artifact.artifactId() + " text=" + text);
    }

    private static String describePart(Part<?> part) {
        return part instanceof TextPart textPart ? textPart.text() : part.toString();
    }

    public String extractText(Task task) {
        StringJoiner text = new StringJoiner("\n");
        if (task.artifacts() != null) {
            for (Artifact a : task.artifacts()) {
                for (Part<?> part : a.parts()) {
                    if (part instanceof TextPart textPart) {
                        text.add(textPart.text());
                    }
                }
            }
        }
        return text.toString();
    }

    public Task getTask(String taskId) throws Exception {
        trace("Looking up stored task " + taskId);
        return client.getTask(new TaskQueryParams(taskId), null);
    }

    private record ArtifactContents(String name, List<String> parts) {
    }

    private Map<String, ArtifactContents> artifactContents(Task task) {
        Map<String, ArtifactContents> artifacts = new LinkedHashMap<>();
        if (task.artifacts() != null) {
            for (Artifact artifact : task.artifacts()) {
                artifacts.put(artifact.artifactId(), new ArtifactContents(artifact.name(),
                        artifact.parts().stream().map(EnterpriseClient::describePart).toList()));
            }
        }
        return artifacts;
    }

    private void verifyStoredTask(String taskId, Task streamedTask) throws Exception {
        Task task = getTask(taskId);
        Map<String, ArtifactContents> storedArtifacts = artifactContents(task);
        Map<String, ArtifactContents> streamedArtifacts = artifactContents(streamedTask);
        String result = "GetTask: " + task.id()
                + " state=" + task.status().state() + " artifacts=" + storedArtifacts.size();
        if (!taskId.equals(task.id())
                || task.status().state() != TaskState.TASK_STATE_COMPLETED
                || !streamedArtifacts.equals(storedArtifacts)) {
            throw new IllegalStateException("Stored task verification failed: " + nodeLabel + " " + result
                    + "; expected artifacts=" + streamedArtifacts + "; stored artifacts=" + storedArtifacts);
        }
        trace(result + " (all artifact IDs, names, and contents match the stream)");
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    /**
     * Runs the full cross-node demo:
     *  1. Creates a task via Node A (init handshake — leaves task open)
     *  2. Resubscribes to that task via Node B (which has never seen it locally)
     *  3. Sends the real work continuation back to Node A
     *  4. Observes WORKING → COMPLETED on Node B, arriving purely via Kafka replication
     *  5. Uses fresh clients to retrieve and verify the completed task from both nodes
     */
    public static String runCrossNodeDemo(String protocol, String message) throws Exception {
        long startedAt = System.nanoTime();
        String taskId;
        try (EnterpriseClient nodeA = new EnterpriseClient(protocol, NODE_A_PORT_OFFSET, false, true, startedAt)) {
            taskId = nodeA.sendInitMessage();
        }
        Task completedTask;
        try (EnterpriseClient nodeB = new EnterpriseClient(protocol, NODE_B_PORT_OFFSET, true, false, startedAt)) {
            CompletableFuture<Task> completion = nodeB.resubscribeUntilCompleted(taskId);

            // Give Node B's subscription time to reach the server before Node A starts producing
            // events, otherwise the earliest events could be missed.
            Thread.sleep(1000);

            nodeB.trace("Waiting for COMPLETED for task " + taskId);
            try (EnterpriseClient nodeA = new EnterpriseClient(protocol, NODE_A_PORT_OFFSET, false, true, startedAt)) {
                nodeA.sendContinuationMessage(taskId, message);
            }

            completedTask = completion.get(15, TimeUnit.SECONDS);
        }

        // Retrieve the saved task after closing the streaming client. Each fresh client
        // must see the same completed task and all artifacts through the shared TaskStore.
        try (EnterpriseClient nodeA = new EnterpriseClient(protocol, NODE_A_PORT_OFFSET, false, false, startedAt);
                EnterpriseClient nodeB = new EnterpriseClient(protocol, NODE_B_PORT_OFFSET, false, false, startedAt)) {
            nodeA.verifyStoredTask(taskId, completedTask);
            nodeB.verifyStoredTask(taskId, completedTask);
            return nodeB.extractText(completedTask);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalStateException("Usage: EnterpriseClient <protocol> <prompt>\n"
                    + "  protocol: JSONRPC | HTTP+JSON | GRPC\n"
                    + "  prompt:   e.g. \"Feedback for Mario Fusco, rating: 5, great talk!\"\n"
                    + "            or   \"Summary for Mario Fusco\"");
        }
        String response = runCrossNodeDemo(args[0], args[1]);
        System.out.println("Agent responds:\n" + response);
        System.exit(0);
    }
}
