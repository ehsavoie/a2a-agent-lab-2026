package dev.devconf.expense;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.server.multitenancy.Tenant;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

@ApplicationScoped
public class ExpenseAgentCardProducer {

    @ConfigProperty(name = "expense.a2a.agent.name")
    String agentName;

    @ConfigProperty(name = "expense.a2a.agent.description")
    String agentDescription;

    @ConfigProperty(name = "a2a.agent.version")
    String agentVersion;

    @ConfigProperty(name = "a2a.agent.url")
    String agentUrl;

    @Produces
    @PublicAgentCard
    @Singleton
    @Tenant("expense")
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name(agentName)
                .description(agentDescription)
                .version(agentVersion)
                .supportedInterfaces(List.of(
                    new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), agentUrl, "expense")
                ))
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .skills(List.of(
                    AgentSkill.builder()
                        .id("expense-logging")
                        .name("Expense Logging")
                        .description("Log and validate expense entries against corporate compliance rules.")
                        .tags(List.of("expenses", "logging", "compliance"))
                        .examples(List.of(
                            "Log a taxi fare of €45 from Bolt Belgium on 2026-10-07",
                            "I spent €22 on lunch at the convention center"))
                        .build(),
                    AgentSkill.builder()
                        .id("receipt-processing")
                        .name("Receipt Processing")
                        .description("Process structured receipt data from other agents into audit-ready expense entries.")
                        .tags(List.of("receipts", "processing", "audit"))
                        .examples(List.of(
                            "Process this receipt: vendor=Bolt Belgium, amount=45.00, currency=EUR, date=2026-10-07"))
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
}
