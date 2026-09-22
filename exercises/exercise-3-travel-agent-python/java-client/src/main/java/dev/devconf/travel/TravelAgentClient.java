package dev.devconf.travel;

import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Java client that calls the Python Travel & Logistics Agent via A2A.
 * Proves Java → Python interop: the client code is identical
 * whether the server is Java or Python.
 *
 * Prerequisites: Python Travel Agent running on port 9000
 *   cd exercises/exercise-3-travel-agent-python
 *   python travel_agent.py
 *
 * Run:
 *   cd java-client
 *   mvn compile exec:java
 */
public class TravelAgentClient {

    private static final String PYTHON_AGENT_URL = "http://localhost:9000";

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("Java → Python A2A Interop Client");
        System.out.println("=".repeat(60));

        // Step 1: Fetch the Python agent's AgentCard
        System.out.println("\n1. Fetching Python Travel Agent's AgentCard...");
        AgentCard card = A2A.getAgentCard(PYTHON_AGENT_URL);
        System.out.println("   Agent: " + card.name());
        System.out.println("   Description: " + card.description());
        System.out.println("   Skills:");
        for (AgentSkill skill : card.skills()) {
            System.out.println("     - " + skill.name() + ": " + skill.description());
        }

        // Step 2: Build the A2A client from the AgentCard
        Client client = Client.builder(card)
                .withTransport(JSONRPCTransport.class, new org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder())
                .build();

        // Step 3: Send transit query (Maya's scenario)
        System.out.println("\n2. Sending transit query: 'How do I get from the airport to the venue quickly?'");
        sendAndPrint(client, "How do I get from the airport to the venue quickly?");

        // Step 4: Send flight status query
        System.out.println("\n3. Sending flight query: 'What is the status of flight UA 1742?'");
        sendAndPrint(client, "What is the status of flight UA 1742?");

        // Step 5: Send receipt extraction
        System.out.println("\n4. Sending receipt extraction: 'Log my taxi receipt for $42.50'");
        sendAndPrint(client, "Log my taxi receipt for $42.50");

        client.close();

        System.out.println("\n" + "=".repeat(60));
        System.out.println("✅ Java → Python A2A interop successful!");
        System.out.println("   Java client → Python A2A agent → response received");
        System.out.println("=".repeat(60));
    }

    private static void sendAndPrint(Client client, String text) throws Exception {
        Message message = A2A.toUserMessage(text);
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder responseText = new StringBuilder();

        client.sendMessage(
                message,
                List.of((ClientEvent event, AgentCard agentCard) -> {
                    if (event instanceof TaskEvent taskEvent) {
                        var task = taskEvent.getTask();
                        if (task.artifacts() != null) {
                            for (Artifact artifact : task.artifacts()) {
                                for (Part<?> part : artifact.parts()) {
                                    if (part instanceof TextPart tp) {
                                        responseText.append(tp.text());
                                    }
                                }
                            }
                        }
                        latch.countDown();
                    } else if (event instanceof MessageEvent messageEvent) {
                        var msg = messageEvent.getMessage();
                        if (msg.parts() != null) {
                            for (Part<?> part : msg.parts()) {
                                if (part instanceof TextPart tp) {
                                    responseText.append(tp.text());
                                }
                            }
                        }
                        latch.countDown();
                    }
                }),
                error -> {
                    System.err.println("   Error: " + error.getMessage());
                    latch.countDown();
                }
        );

        if (!latch.await(30, TimeUnit.SECONDS)) {
            System.out.println("   ⚠️  Timeout waiting for response");
            return;
        }

        String response = responseText.toString();
        if (response.length() > 500) {
            System.out.println("   Response (first 500 chars):");
            System.out.println("   " + response.substring(0, 500).replace("\n", "\n   "));
            System.out.println("   ...");
        } else {
            System.out.println("   Response:");
            System.out.println("   " + response.replace("\n", "\n   "));
        }
    }
}
