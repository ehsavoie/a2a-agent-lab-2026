package dev.devconf.enterprise.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;

/**
 * Cross-node demo: creates a task via node A (port 8080), resubscribes via node B (port 9080),
 * then sends the real work back to node A. Updates observed on node B arrive purely through
 * Kafka replication — node B never executed any business logic for this task.
 *
 * Run with:
 *   cd exercises/exercise-6-enterprise/client
 *   mvn exec:java -Prun-jsonrpc   (or -Prun-grpc / -Prun-rest)
 */
public class EnterpriseClient implements AutoCloseable {

    private static final int BASE_HTTP_PORT = 8080;
    private static final int NODE_A_PORT_OFFSET = 0;
    private static final int NODE_B_PORT_OFFSET = 1000;

    private final Client client;

    public EnterpriseClient(String protocol, int nodePortOffset) throws Exception {
        this(protocol, nodePortOffset, true, false);
    }

    /**
     * @param streaming whether to use SSE streaming (required for resubscribe)
     * @param polling   whether message/send returns immediately after task creation
     */
    public EnterpriseClient(String protocol, int nodePortOffset, boolean streaming, boolean polling) throws Exception {
        String cardBaseUrl = "http://localhost:" + (BASE_HTTP_PORT + nodePortOffset);
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

    /**
     * Sends the init handshake message to node A. The agent detects messageId="init" and
     * parks the task open so a cross-node continuation can be sent later.
     *
     * @return the taskId assigned by node A (stored in PostgreSQL, visible to node B via JPA)
     */
    public String sendInitMessage() throws Exception {
        Message initMessage = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId("init")
                .parts(List.of(new TextPart("init")))
                .build();

        CompletableFuture<String> taskId = new CompletableFuture<>();
        BiConsumer<ClientEvent, AgentCard> consumer = (event, agentCard) -> {
            if (event instanceof TaskEvent taskEvent) {
                taskId.complete(taskEvent.getTask().id());
            } else {
                taskId.completeExceptionally(new IllegalStateException("Expected a TaskEvent"));
            }
        };
        client.sendMessage(initMessage, Collections.singletonList(consumer), null, null);
        return taskId.get(10, TimeUnit.SECONDS);
    }

    /**
     * Sends the real work continuation to the task on this node.
     */
    public void sendContinuationMessage(String taskId, String name) throws Exception {
        Message continuation = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .taskId(taskId)
                .parts(List.of(new TextPart(name)))
                .build();
        client.sendMessage(continuation, Collections.emptyList(), null, null);
    }

    /**
     * Resubscribes to taskId on this node and collects every state/artifact until COMPLETED.
     * Node B has never executed this task — all updates it sees arrive via Kafka replication.
     */
    public CompletableFuture<List<TaskState>> resubscribeAndCollectStates(String taskId, CompletableFuture<String> artifactText) {
        List<TaskState> observedStates = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<List<TaskState>> completion = new CompletableFuture<>();

        BiConsumer<ClientEvent, AgentCard> consumer = (event, agentCard) -> {
            if (event instanceof TaskUpdateEvent update) {
                TaskState state = update.getTask().status().state();
                if (update.getUpdateEvent() instanceof TaskStatusUpdateEvent) {
                    observedStates.add(state);
                }
                if (update.getUpdateEvent() instanceof TaskArtifactUpdateEvent artifactUpdate) {
                    for (Part<?> part : artifactUpdate.artifact().parts()) {
                        if (part instanceof TextPart textPart) {
                            artifactText.complete(textPart.text());
                        }
                    }
                }
                if (state == TaskState.TASK_STATE_COMPLETED) {
                    completion.complete(new ArrayList<>(observedStates));
                }
            }
        };

        Thread subscriberThread = new Thread(() -> {
            try {
                client.subscribeToTask(new TaskIdParams(taskId), Collections.singletonList(consumer),
                        completion::completeExceptionally, null);
            } catch (Exception e) {
                completion.completeExceptionally(e);
            }
        }, "resubscribe-" + taskId);
        subscriberThread.setDaemon(true);
        subscriberThread.start();
        return completion;
    }

    public String extractText(Task task) {
        StringBuilder sb = new StringBuilder();
        if (task.artifacts() != null) {
            for (Artifact a : task.artifacts()) {
                for (Part<?> part : a.parts()) {
                    if (part instanceof TextPart textPart) {
                        sb.append(textPart.text());
                    }
                }
            }
        }
        return sb.toString();
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    /**
     * Full cross-node demo:
     * 1. Create task via node A (init handshake, task stays open)
     * 2. Resubscribe via node B (which has never seen this task locally)
     * 3. Send real work via node A — node B observes updates through Kafka
     */
    public static String runCrossNodeDemo(String protocol, String name) throws Exception {
        String taskId;
        try (EnterpriseClient nodeA = new EnterpriseClient(protocol, NODE_A_PORT_OFFSET, false, true)) {
            taskId = nodeA.sendInitMessage();
        }
        System.out.println("Created task " + taskId + " via node A");

        CompletableFuture<String> artifactText = new CompletableFuture<>();
        EnterpriseClient nodeB = new EnterpriseClient(protocol, NODE_B_PORT_OFFSET, true, false);
        try {
            CompletableFuture<List<TaskState>> statesFuture = nodeB.resubscribeAndCollectStates(taskId, artifactText);

            // Give node B's subscription time to reach the server before node A starts producing events
            Thread.sleep(1000);

            try (EnterpriseClient nodeA = new EnterpriseClient(protocol, NODE_A_PORT_OFFSET, false, true)) {
                nodeA.sendContinuationMessage(taskId, name);
            }

            String response = artifactText.get(15, TimeUnit.SECONDS);
            List<TaskState> states = statesFuture.get(15, TimeUnit.SECONDS);
            System.out.println("Node B observed states (via Kafka replication): " + states);
            return response;
        } finally {
            nodeB.close();
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalStateException("Usage: EnterpriseClient <protocol> <name>\n" +
                    "  protocol: JSONRPC | GRPC | HTTP+JSON\n" +
                    "  name: your name (sent as the greeting payload)");
        }
        String response = runCrossNodeDemo(args[0], args[1]);
        System.out.println("Agent responds: " + response);
        System.exit(0);
    }
}
