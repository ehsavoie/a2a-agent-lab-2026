package dev.devconf.expense.client;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.rest.RestTransport;
import org.a2aproject.sdk.client.transport.rest.RestTransportConfigBuilder;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;

@ApplicationScoped
@Path("/expense")
public class ExpenseClientResource {

    private static final String AGENT_BASE_URL = "http://localhost:8082";

    public record ExpenseRequest(String vendor, String amount, String currency,
                                 String date, String category, String description) {}
    public record ExpenseResponse(String response) {}
    public record ErrorResponse(String error) {}

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response submitExpense(ExpenseRequest req) {
        if (req == null || req.vendor() == null || req.vendor().isBlank()) {
            return badRequest("vendor is required");
        }
        String query = "Log an expense: vendor is " + req.vendor()
                + ", amount is " + req.amount()
                + " " + req.currency()
                + ", date is " + req.date()
                + ", category is " + req.category()
                + ", description is " + req.description();
        return askAgent(query);
    }

    @GET
    @Path("/summary")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSummary() {
        return askAgent("Get the expense summary for all logged entries.");
    }

    @GET
    @Path("/compliance")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCompliance() {
        return askAgent("Check the compliance status and list any flagged or over-limit expenses.");
    }

    private Response askAgent(String query) {
        try {
            AgentCard agentCard = A2ACardResolver.builder()
                    .baseUrl(AGENT_BASE_URL)
                    .build()
                    .getAgentCard();

            try (Client client = Client.builder(agentCard)
                    .withTransport(RestTransport.class, new RestTransportConfigBuilder())
                    .build()) {

                Message message = Message.builder()
                        .role(Message.Role.ROLE_USER)
                        .parts(List.of(new TextPart(query)))
                        .build();

                CompletableFuture<String> result = new CompletableFuture<>();

                client.sendMessage(message,
                        Collections.singletonList((ClientEvent event, AgentCard card) -> {
                            if (event instanceof TaskUpdateEvent update) {
                                if (update.getUpdateEvent() instanceof TaskStatusUpdateEvent statusUpdate) {
                                    if (statusUpdate.status().state() == TaskState.TASK_STATE_FAILED) {
                                        result.completeExceptionally(
                                                new RuntimeException("Agent task failed"));
                                    }
                                }
                                if (update.getTask().status().state() == TaskState.TASK_STATE_COMPLETED) {
                                    result.complete(extractText(update.getTask().artifacts()));
                                }
                            }
                        }),
                        error -> result.completeExceptionally(new RuntimeException(error.getMessage())),
                        null);

                String text = result.get(30, TimeUnit.SECONDS);
                return Response.ok(new ExpenseResponse(text)).build();
            }
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        }
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(message))
                .build();
    }

    private String extractText(List<Artifact> artifacts) {
        if (artifacts == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Artifact artifact : artifacts) {
            for (Part<?> part : artifact.parts()) {
                if (part instanceof TextPart textPart) {
                    sb.append(textPart.text());
                }
            }
        }
        return sb.toString();
    }
}
