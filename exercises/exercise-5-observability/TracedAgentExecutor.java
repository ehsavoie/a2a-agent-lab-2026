package dev.devconf.session;

import java.util.Collections;
import java.util.List;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;

/**
 * Example: Wrap the AgentExecutor with custom OTel spans.
 * Replace the anonymous AgentExecutor in SessionAgentExecutorProducer
 * with this class to get fine-grained tracing.
 */
public class TracedAgentExecutor implements AgentExecutor {

    private final SessionService sessionService;
    private final Tracer tracer;

    public TracedAgentExecutor(SessionService sessionService, Tracer tracer) {
        this.sessionService = sessionService;
        this.tracer = tracer;
    }

    @Override
    public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
        Span span = tracer.spanBuilder("session-agent.execute")
                .setAttribute("a2a.task.id", context.getTask() != null
                        ? context.getTask().id() : "unknown")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            String userText = extractText(context.getMessage());
            span.setAttribute("a2a.user.query", userText);

            emitter.startWork();

            Span llmSpan = tracer.spanBuilder("langchain4j.chat")
                    .startSpan();
            String response;
            try (Scope llmScope = llmSpan.makeCurrent()) {
                response = sessionService.chat(userText);
                llmSpan.setAttribute("a2a.response.length", response.length());
            } finally {
                llmSpan.end();
            }

            emitter.addArtifact(Collections.singletonList(new TextPart(response)), null, "response", null);
            emitter.complete();
            span.setAttribute("a2a.status", "completed");
        } catch (Exception e) {
            span.recordException(e);
            emitter.addArtifact(
                    Collections.singletonList(new TextPart("Sorry, I encountered an error: " + e.getMessage())),
                    null, "error", null);
            emitter.complete();
        } finally {
            span.end();
        }
    }

    @Override
    public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
        throw new TaskNotCancelableError();
    }

    private String extractText(Message message) {
        if (message == null || message.parts() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                sb.append(textPart.text());
            }
        }
        return sb.toString().trim();
    }
}
