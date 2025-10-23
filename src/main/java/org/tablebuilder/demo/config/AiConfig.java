package org.tablebuilder.demo.config;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;


import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tablebuilder.demo.service.PostgresChatMemory;
import org.tablebuilder.demo.store.ChatRepository;


@Configuration
public class AiConfig {
    @Autowired
    private VectorStore vectorStore;
    @Autowired
    private ChatRepository chatRepository;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${spring.ai.ollama.chat.options.model:gemma3:4b-it-q4_K_M}")
    private String chatModelName;

    @Value("${spring.ai.ollama.embedding.options.model:nomic-embed-text}")
    private String embeddingModelName;

    // Промпт-шаблон для RAG
    private static final String RAG_PROMPT_TEMPLATE = """
            Ты - AI ассистент для поиска данных в системе. Используй предоставленную информацию для ответа на вопрос.
            Контекстная информация:
            {context}
            
            Вопрос пользователя: {question}
            
            Ответь на русском языке, основываясь на контекстной информации.
            Если информации недостаточно, скажи об этом.
            """;


    /**
     * Бин для ChatMemory (PostgreSQL реализация)
     */
    @Bean
    public ChatMemory chatMemory() {
        return new PostgresChatMemory(chatRepository );
    }

    /**
     * Бин ChatClient с Advisor'ами
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 VectorStore vectorStore,
                                 ChatMemory chatMemory) {
        return builder
                .defaultAdvisors(
                        getHistoryAdvisor(chatMemory),
                        getRagAdvisor(vectorStore)
                )
                .defaultSystem(RAG_PROMPT_TEMPLATE)
                .build();
    }

    /**
     * Advisor для работы с историей сообщений
     */
    private Advisor getHistoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .order(-10) // Высокий приоритет
                .build();
    }

    /**
     * Advisor для RAG (Retrieval Augmented Generation)
     */
    private Advisor getRagAdvisor(VectorStore vectorStore) {
//        // Создаем PromptTemplate
//        PromptTemplate promptTemplate = new PromptTemplate(RAG_PROMPT_TEMPLATE);


        // Собираем QuestionAnswerAdvisor
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder().similarityThreshold(0.8d).topK(3).build())
                .build();
    }

}