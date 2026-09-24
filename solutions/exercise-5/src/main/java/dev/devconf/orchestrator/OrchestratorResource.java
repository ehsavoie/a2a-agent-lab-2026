package dev.devconf.orchestrator;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api")
public class OrchestratorResource {

    @Inject
    OrchestratorSupervisor supervisor;

    @POST
    @Path("/query")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public QueryResponse query(QueryRequest request) {
        String response = supervisor.orchestrate(request.query());
        return new QueryResponse(response);
    }

    public record QueryRequest(String query) {}

    public record QueryResponse(String response) {}
}
