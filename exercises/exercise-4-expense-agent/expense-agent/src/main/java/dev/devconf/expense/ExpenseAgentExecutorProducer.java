package dev.devconf.expense;

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
public class ExpenseAgentExecutorProducer {

    @Inject
    ExpenseServiceProducer expenseServiceProducer;

    @Produces
    @ApplicationScoped
    public AgentExecutor agentExecutor() {
        // TODO: Return an anonymous AgentExecutor implementation.
        //
        // In execute(RequestContext, AgentEmitter):
        //   1. Extract user text using extractText(context.getMessage())
        //   2. If context.getTask() == null (new task), call emitter.submit() first
        //   3. Call emitter.startWork()
        //   4. Call expenseServiceProducer.getExpenseService().chat(userText)
        //   5. On success: emitter.addArtifact(Collections.singletonList(new TextPart(response)), null, "response", null), then emitter.complete()
        //   6. On exception: emitter.addArtifact(...) with error message, then emitter.fail()
        //
        // In cancel(RequestContext, AgentEmitter):
        //   Throw new TaskNotCancelableError()
        return null;
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
