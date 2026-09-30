package dev.devconf.expense;

import java.util.ArrayList;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.ConfigProvider;

@ApplicationScoped
public class ExpenseAgentCardProducer {

    private static final int BASE_HTTP_PORT = 8080;
    private static final int BASE_GRPC_PORT = 9555;

    @Produces
    @PublicAgentCard
    public AgentCard agentCard() {
        int portOffset = ConfigProvider.getConfig()
                .getOptionalValue("jboss.socket.binding.port-offset", Integer.class)
                .orElse(0);

        String jsonRpcUrl = "http://localhost:" + (BASE_HTTP_PORT + portOffset);
        List<AgentInterface> interfaces = new ArrayList<>();
        interfaces.add(
                new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), jsonRpcUrl));
        if (isRest()) {
            interfaces.add(
                    new AgentInterface(
                            TransportProtocol.HTTP_JSON.asString(), jsonRpcUrl));
        }
        if (isGrpcEnabled()) {
            interfaces.add(
                    new AgentInterface(
                            TransportProtocol.GRPC.asString(), "localhost:" + (BASE_GRPC_PORT + portOffset)));
        }

        return AgentCard.builder()
                .name("Expense & Compliance Agent")
                .description("Standardizes receipts into corporate audit-ready expense logs with compliance validation")
                .version("1.0.0")
                .supportedInterfaces(interfaces)
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .build())
                .skills(List.of(
                    AgentSkill.builder()
                        .id("expense-logging")
                        .name("Expense Logging")
                        .description("Log and validate expense entries against corporate compliance rules.")
                        .tags(List.of("expenses", "logging", "compliance"))
                        .examples(List.of(
                            "Log a $45 taxi from Airport Cabs on 2026-10-07",
                            "I spent $22 on lunch at the convention center"))
                        .build(),
                    AgentSkill.builder()
                        .id("receipt-processing")
                        .name("Receipt Processing")
                        .description("Process receipt data from other agents into audit-ready expense entries.")
                        .tags(List.of("receipts", "processing", "audit"))
                        .examples(List.of(
                            "Process this receipt: vendor=Yellow Cab, amount=$45.00, date=2026-10-07"))
                        .build(),
                    AgentSkill.builder()
                        .id("compliance-report")
                        .name("Compliance Report")
                        .description("Generate compliance status reports and flag policy violations.")
                        .tags(List.of("compliance", "reporting", "audit"))
                        .examples(List.of(
                            "Show my expense summary",
                            "Are all my expenses compliant?"))
                        .build()
                ))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .build();
    }

    private boolean isGrpcEnabled() {
        try {
            Class.forName("org.wildfly.a2a.jakarta.grpc.GrpcBeanInitializer");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isRest() {
        try {
            Class.forName("org.wildfly.a2a.jakarta.rest.WildFlyRestTransportMetadata");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
