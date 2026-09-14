package dev.devconf.session;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;

@ApplicationScoped
public class SessionServiceProducer {

    private static final String OLLAMA_URL = "http://localhost:11434";
    private static final String MODEL = "mistral";

    private SessionService sessionService;

    @PostConstruct
    void init() {
        OllamaChatModel chatModel = OllamaChatModel.builder()
                .baseUrl(OLLAMA_URL)
                .modelName(MODEL)
                .temperature(0.7)
                .build();

        SessionTool sessionTool = new SessionTool();

        sessionService = AiServices.builder(SessionService.class)
                .chatModel(chatModel)
                .tools(sessionTool)
                .build();
    }

    public SessionService getSessionService() {
        return sessionService;
    }
}
