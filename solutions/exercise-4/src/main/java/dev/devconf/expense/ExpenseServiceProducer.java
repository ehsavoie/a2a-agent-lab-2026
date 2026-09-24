package dev.devconf.expense;

import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.model.openai.OpenAiResponsesChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;

@ApplicationScoped
public class ExpenseServiceProducer {

    @Inject
    @ConfigProperty(name = "openai.api-key")
    String openAiApiKey;

    @Inject
    @ConfigProperty(name = "openai.model-name", defaultValue = "gpt-6-luna")
    String openAiModelName;

    private ExpenseService expenseService;

    @PostConstruct
    void init() {
        OpenAiResponsesChatModel chatModel = OpenAiResponsesChatModel.builder()
                .httpClientBuilder(HttpClientBuilderLoader.loadHttpClientBuilder()
                        .readTimeout(Duration.ofSeconds(120)))
                .apiKey(openAiApiKey)
                .modelName(openAiModelName)
                .reasoningEffort("medium")
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
