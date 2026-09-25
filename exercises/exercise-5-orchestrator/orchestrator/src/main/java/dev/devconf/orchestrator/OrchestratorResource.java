package dev.devconf.orchestrator;

import java.util.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
public class OrchestratorResource {

    private static final Logger LOG = Logger.getLogger(OrchestratorResource.class.getName());

    @Inject
    OrchestratorSupervisor supervisor;

    @POST
    @Path("/query")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public QueryResponse query(QueryRequest request) {
        LOG.info("Received query: " + request.query());
        LOG.info("Supervisor class: " + supervisor.getClass().getName());
        String response = supervisor.orchestrate(request.query()).result();
        LOG.info("Supervisor response: " + response);
        return new QueryResponse(response);
    }

    public record QueryRequest(String query) {}

    public record QueryResponse(String response) {}
}
