package dev.devconf.orchestrator;

import java.util.logging.Logger;

import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;

public class DebugAgentListener implements AgentListener {

    private static final Logger LOG = Logger.getLogger(DebugAgentListener.class.getName());

    @Override
    public void beforeAgentInvocation(AgentRequest request) {
        LOG.info("=== BEFORE agent invocation: " + request.agentName() + " ===");
        LOG.info("Agent inputs: " + request.inputs());
        AgenticScope scope = request.agenticScope();
        if (scope != null) {
            LOG.info("Scope state keys: " + scope.state().keySet());
        } else {
            LOG.warning("Scope is NULL");
        }
    }

    @Override
    public void afterAgentInvocation(AgentResponse response) {
        LOG.info("=== AFTER agent invocation: " + response.agentName() + " ===");
    }

    @Override
    public boolean inheritedBySubagents() {
        return true;
    }
}
