package dev.devconf.enterprise;

import java.util.Collections;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TaskNotCancelableError;
import org.a2aproject.sdk.spec.TextPart;

@ApplicationScoped
public class EnterpriseAgentExecutorProducer {

    @Produces
    @ApplicationScoped
    public AgentExecutor enterpriseAgentExecutor() {
        return new EnterpriseAgentExecutor();
    }

    private static class EnterpriseAgentExecutor implements AgentExecutor {

        @Override
        public void execute(RequestContext context, AgentEmitter emitter) throws A2AError {
            boolean isNewTask = context.getTask() == null;
            if (isNewTask) {
                emitter.submit();
            }

            // Demo-only convention (not a general A2A pattern): a message with messageId "init"
            // only creates the task and returns, leaving it open for a later continuation
            // message to do the work. This lets a client observe the task via a different node
            // than the one that eventually completes it, proving cross-node Kafka replication.
            boolean isInitHandshake = isNewTask && "init".equals(context.getMessage().messageId());
            if (isInitHandshake) {
                return;
            }

            emitter.startWork();
            pause(2000);

            List<Part<?>> parts = context.getMessage().parts();
            List<TextPart> textParts = parts.stream()
                    .filter(p -> p instanceof TextPart)
                    .map(p -> (TextPart) p)
                    .toList();
            String name = textParts.get(textParts.size() - 1).text();

            emitter.addArtifact(
                    Collections.singletonList(new TextPart("Hello " + name)),
                    null, "greeting", null);
            pause(1000);
            emitter.addArtifact(
                    Collections.singletonList(new TextPart("Preparing your workshop welcome, " + name)),
                    null, "progress", null);
            pause(1000);
            emitter.addArtifact(
                    Collections.singletonList(new TextPart("Welcome to the enterprise A2A demo, " + name + "!")),
                    null, "summary", null);
            emitter.complete();
        }

        private void pause(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        @Override
        public void cancel(RequestContext context, AgentEmitter emitter) throws A2AError {
            throw new TaskNotCancelableError();
        }
    }
}
