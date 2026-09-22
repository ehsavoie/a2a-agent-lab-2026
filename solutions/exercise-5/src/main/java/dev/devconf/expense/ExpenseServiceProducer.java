package dev.devconf.expense;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ExpenseServiceProducer {

    @ConfigProperty(name = "ollama.base-url", defaultValue = "http://localhost:11434")
    String ollamaBaseUrl;

    @ConfigProperty(name = "ollama.model-name", defaultValue = "granite4.1:3b")
    String ollamaModelName;

    private ExpenseService expenseService;

    @PostConstruct
    void init() {
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaModelName)
                .temperature(0.7)
                .timeout(java.time.Duration.ofSeconds(120))
                .build();

        expenseService = AiServices.builder(ExpenseService.class)
                .chatModel(chatModel)
                .tools(new ExpenseTool())
                .build();
    }

    public ExpenseService getExpenseService() {
        return expenseService;
    }
}
