package dev.devconf.travel;

import java.util.Collections;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;

@ApplicationScoped
public class TravelAgentExecutorProducer {

    @Inject
    TravelService travelService;

    @Produces
    public AgentExecutor agentExecutor() {
        return new AgentExecutor() {

            @Override
            public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
                String userText = extractText(context.getMessage());

                emitter.startWork();

                try {
                    String response = travelService.chat(userText);

                    emitter.addArtifact(
                            Collections.singletonList(new TextPart(response)),
                            null, "response", null);
                    emitter.complete();
                } catch (Exception e) {
                    emitter.addArtifact(
                            Collections.singletonList(new TextPart("Error: " + e.getMessage())),
                            null, "error", null);
                    emitter.complete();
                }
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
                throw new TaskNotCancelableError();
            }
        };
    }

    private String extractText(Message message) {
        if (message == null || message.parts() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                sb.append(textPart.text());
            }
        }
        return sb.toString().trim();
    }
}
