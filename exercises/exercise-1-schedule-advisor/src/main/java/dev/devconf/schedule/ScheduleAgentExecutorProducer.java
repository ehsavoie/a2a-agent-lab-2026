package dev.devconf.schedule;

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
public class ScheduleAgentExecutorProducer {

    @Inject
    ScheduleService scheduleService;

    @Produces
    public AgentExecutor agentExecutor() {
        // TODO: Return an anonymous AgentExecutor implementation.
        //
        // In execute(RequestContext, AgentEmitter):
        //   1. Extract the user text from context.getMessage() (iterate parts, collect TextPart values)
        //   2. Call emitter.startWork() to signal the task is running
        //   3. Call scheduleService.chat(userText) to get the LLM response
        //   4. Call emitter.addArtifact(...) with a TextPart wrapping the response
        //   5. Call emitter.complete() on success, or emitter.fail() on exception
        //
        // In cancel(RequestContext, AgentEmitter):
        //   Throw new TaskNotCancelableError()
        return null;
    }
}
