package dev.devconf.expense;

import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ExpenseServiceProducer {

    @ConfigProperty(name = "gemini.api-key")
    String geminiApiKey;

    @ConfigProperty(name = "gemini.model-name", defaultValue = "gemini-2.5-flash")
    String geminiModelName;

    private ExpenseService expenseService;

    @PostConstruct
    void init() {
        GoogleAiGeminiChatModel chatModel = GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName(geminiModelName)
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
