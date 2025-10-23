package org.tablebuilder.demo.service;


import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.Message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.tablebuilder.demo.store.ChatMessage;
import org.tablebuilder.demo.store.ChatRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class PostgresChatMemory implements ChatMemory {

    private final ChatRepository chatRepository;
    @Value("${app.chat.memory.max-messages:12}") // Читаем значение из application.yml, 12 - значение по умолчанию
    private int maxMessages;
    private final ConcurrentMap<String, List<Message>> memoryCache = new ConcurrentHashMap<>();

    public PostgresChatMemory(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;

    }

    public static PostgresChatMemoryBuilder builder() {
        return new PostgresChatMemoryBuilder();
    }


    @Override
    public void add(String conversationId, Message message) {
        // Сохраняем одно сообщение
        if (message instanceof UserMessage) {
            saveMessage(conversationId, message, message.getText(), "user");
        } else if (message instanceof AssistantMessage) {
            saveMessage(conversationId, message, message.getText(), "assistant");
        } else {
            // Для других типов сообщений
            saveMessage(conversationId, message, message.getText(), message.getClass().getSimpleName().toLowerCase());
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        // Сохраняем список сообщений
        for (Message message : messages) {
            add(conversationId, message);
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        // Проверяем кэш
        if (memoryCache.containsKey(conversationId)) {
            List<Message> allMessages = memoryCache.get(conversationId);
            return getLastNMessages(allMessages, lastN);
        }

        // Загружаем из базы данных
        List<ChatMessage> dbMessages = chatRepository.findByChatIdOrderByMessageOrderAsc(conversationId);
        List<Message> messages = convertToAiMessages(dbMessages);

        // Сохраняем в кэш
        memoryCache.put(conversationId, messages);

        // Возвращаем последние N сообщений
        return getLastNMessages(messages, lastN);
    }


    public List<Message> get(String conversationId) {
        // Проверяем кэш
        if (memoryCache.containsKey(conversationId)) {
            return memoryCache.get(conversationId);
        }

        // Загружаем из базы данных
        List<ChatMessage> dbMessages = chatRepository.findByChatIdOrderByMessageOrderAsc(conversationId);
        List<Message> messages = convertToAiMessages(dbMessages);

        // Сохраняем в кэш
        memoryCache.put(conversationId, messages);

        // Возвращаем последние maxMessages сообщений
        return messages.stream()
                .limit(maxMessages)
                .toList();
    }


    @Override
    public void clear(String conversationId) {
        // Очищаем историю для conversationId
        chatRepository.deleteByChatId(conversationId);
        memoryCache.remove(conversationId);
    }

    /**
     * Сохраняет сообщение в базу данных
     */
    private void saveMessage(String conversationId, Message message, String content, String role) {
        // Получаем текущее количество сообщений
        long messageCount = chatRepository.countByChatId(conversationId);

        // Очищаем старые сообщения если превышен лимит
        if (messageCount >= maxMessages) {
            clearOldMessages(conversationId);
            messageCount = 0; // Сбрасываем счетчик после очистки
        }

        // Сохраняем новое сообщение
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setChatId(conversationId);
        chatMessage.setMessageOrder((int) messageCount);
        chatMessage.setContent(content);
        chatMessage.setRole(role);

        chatRepository.save(chatMessage);

        // Обновляем кэш
        updateCache(conversationId, message);
    }

    /**
     * Очищает старые сообщения, оставляя место для новых
     */
    private void clearOldMessages(String conversationId) {
        List<ChatMessage> allMessages = chatRepository.findByChatIdOrderByMessageOrderAsc(conversationId);

        // Оставляем последние (maxMessages - 1) сообщений
        int messagesToKeep = maxMessages - 1;
        if (allMessages.size() > messagesToKeep) {
            List<ChatMessage> messagesToDelete = allMessages.subList(0, allMessages.size() - messagesToKeep);
            chatRepository.deleteAll(messagesToDelete);

            // Переиндексируем оставшиеся сообщения
            List<ChatMessage> remainingMessages = allMessages.subList(allMessages.size() - messagesToKeep, allMessages.size());
            for (int i = 0; i < remainingMessages.size(); i++) {
                ChatMessage msg = remainingMessages.get(i);
                msg.setMessageOrder(i);
                chatRepository.save(msg);
            }
        }

        // Очищаем кэш
        memoryCache.remove(conversationId);
    }

    /**
     * Обновляет кэш для conversationId
     */
    private void updateCache(String conversationId, Message newMessage) {
        List<Message> messages = memoryCache.getOrDefault(conversationId, new ArrayList<>());
        messages.add(newMessage);

        // Ограничиваем размер кэша
        if (messages.size() > maxMessages) {
            messages = messages.subList(messages.size() - maxMessages, messages.size());
        }

        memoryCache.put(conversationId, messages);
    }

    /**
     * Конвертирует ChatMessage в Spring AI Message
     */
    private List<Message> convertToAiMessages(List<ChatMessage> dbMessages) {
        List<Message> messages = new ArrayList<>();

        for (ChatMessage dbMessage : dbMessages) {
            Message message = createAiMessage(dbMessage);
            if (message != null) {
                messages.add(message);
            }
        }

        return messages;
    }

    /**
     * Создает объект Message из ChatMessage
     */
    private Message createAiMessage(ChatMessage dbMessage) {
        switch (dbMessage.getRole().toLowerCase()) {
            case "user":
                return new UserMessage(dbMessage.getContent());
            case "assistant":
                return new AssistantMessage(dbMessage.getContent());
            default:
                // Для неизвестных типов создаем базовое сообщение
                return new Message() {
                    @Override
                    public String getText() {
                        return dbMessage.getContent();
                    }

                    @Override
                    public Map<String, Object> getMetadata() {
                        return Map.of("id", dbMessage.getId());
                    }

                    @Override
                    public MessageType getMessageType() {
                        return null;
                    }

                };
        }
    }

    /**
     * Возвращает последние N сообщений из списка
     */
    private List<Message> getLastNMessages(List<Message> allMessages, int lastN) {
        if (allMessages.size() <= lastN) {
            return new ArrayList<>(allMessages);
        }
        return new ArrayList<>(allMessages.subList(allMessages.size() - lastN, allMessages.size()));
    }

    public static class PostgresChatMemoryBuilder {
        private ChatRepository chatRepository;
        private int maxMessages = 12;

        public PostgresChatMemoryBuilder chatMemoryRepository(ChatRepository chatRepository) {
            this.chatRepository = chatRepository;
            return this;
        }

        public PostgresChatMemoryBuilder maxMessages(int maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public PostgresChatMemory build() {
            if (chatRepository == null) {
                throw new IllegalStateException("ChatRepository must be set");
            }
            return new PostgresChatMemory(chatRepository);
        }
    }
}