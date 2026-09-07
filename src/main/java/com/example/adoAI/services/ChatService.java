package com.example.adoAI.services;

import com.example.adoAI.Role;
import com.example.adoAI.dtos.ChatHistoryDTO;
import com.example.adoAI.dtos.ChatRequest;
import com.example.adoAI.dtos.ChatResponse;
import com.example.adoAI.entities.Chat;
import com.example.adoAI.entities.Message;
import com.example.adoAI.exceptions.ChatNotFoundException;
import com.example.adoAI.repositories.ChatRepository;
import com.example.adoAI.repositories.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final GeminiService geminiService;

    public ChatService(ChatRepository chatRepository, MessageRepository messageRepository, GeminiService geminiService) {
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.geminiService = geminiService;
    }

    @Transactional
    public ChatResponse sendMessage(ChatRequest request) {
        // 1. Resolve (cria ou busca) a conversa
        Chat chat = request.getChatId() == null
                ? new Chat()
                : chatRepository.findById(request.getChatId())
                .orElseThrow(() -> new ChatNotFoundException(request.getChatId()));

        // 2. Monta o histórico no formato "role: conteúdo"
        String history = buildHistory(chat.getMessages());

        // 3. Chama a IA
        String reply = geminiService.generateResponse(request.getMessage(), history);

        // 4. Persiste a pergunta do usuário
        Message userMsg = new Message();
        userMsg.setRole(Role.USER);
        userMsg.setContent(request.getMessage());
        userMsg.setChat(chat);
        messageRepository.save(userMsg);

        // 5. Persiste a resposta do assistente
        Message aiMsg = new Message();
        aiMsg.setRole(Role.ASSISTANT);
        aiMsg.setContent(reply);
        aiMsg.setChat(chat);
        messageRepository.save(aiMsg);

        // 6. Atualiza o título da conversa (primeira pergunta vira título)
        if (chat.getTitle() == null && chat.getId() == null) {
            String title = request.getMessage();
            chat.setTitle(title.length() > 50 ? title.substring(0, 47) + "..." : title);
        }

        chatRepository.save(chat);

        return new ChatResponse(chat.getId(), reply);
    }

    @Transactional(readOnly = true)
    public List<ChatHistoryDTO> getChatHistory(Long chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException(chatId));
        return chat.getMessages().stream()
                .map(m -> new ChatHistoryDTO(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Chat> getAllChats() {
        return chatRepository.findAll();
    }

    @Transactional
    public void deleteChat(Long chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException(chatId));
        chatRepository.delete(chat);
    }

    private String buildHistory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));
    }
}