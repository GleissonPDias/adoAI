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
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final GeminiService geminiService;
    private final TransactionTemplate transactionTemplate;

    public ChatService(ChatRepository chatRepository, MessageRepository messageRepository,
                       GeminiService geminiService, PlatformTransactionManager transactionManager) {
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.geminiService = geminiService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public ChatResponse sendMessage(ChatRequest request) {
        Chat chat = resolveChat(request.getChatId());

        String history = buildHistory(chat.getMessages());
        String reply = geminiService.generateResponse(request.getMessage(), history);

        saveUserMessage(chat, request.getMessage());
        saveAssistantMessage(chat, reply);
        applyTitle(chat, request.getMessage());
        chatRepository.save(chat);

        return new ChatResponse(chat.getId(), reply);
    }

    /**
     * Fluxo SSE: prepara a conversa em uma transação curta, devolve o chatId no primeiro
     * evento e repassa os deltas da IA em tempo real. A resposta final só é persistida
     * quando o stream termina com sucesso.
     */
    public Flux<ServerSentEvent<String>> streamMessage(ChatRequest request) {
        Prepared prepared = transactionTemplate.execute(status -> {
            Chat chat = resolveChat(request.getChatId());
            String history = buildHistory(chat.getMessages());

            saveUserMessage(chat, request.getMessage());
            applyTitle(chat, request.getMessage());
            chatRepository.save(chat);

            return new Prepared(chat.getId(), history);
        });

        Long chatId = prepared.chatId();
        StringBuilder accumulated = new StringBuilder();

        Flux<ServerSentEvent<String>> meta = Flux.just(
                ServerSentEvent.<String>builder(String.valueOf(chatId)).event("meta").build());

        Flux<ServerSentEvent<String>> chunks = geminiService.streamResponse(request.getMessage(), prepared.history())
                .doOnNext(accumulated::append)
                .map(delta -> ServerSentEvent.builder(delta).event("chunk").build())
                // Persiste antes de emitir 'done', para o histórico já estar consistente
                // quando o frontend reagir ao fim do stream.
                .doOnComplete(() -> transactionTemplate.executeWithoutResult(status ->
                        saveAssistantMessage(chatId, accumulated.toString())))
                .concatWith(Flux.just(ServerSentEvent.<String>builder("").event("done").build()));

        return Flux.concat(meta, chunks);
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

    /* ------------------------------------------------------------------ */
    /* Helpers compartilhados pelos fluxos bloqueante e streaming          */
    /* ------------------------------------------------------------------ */

    private Chat resolveChat(Long chatId) {
        if (chatId == null) {
            return new Chat();
        }
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException(chatId));
    }

    private void saveUserMessage(Chat chat, String message) {
        Message userMsg = new Message();
        userMsg.setRole(Role.USER);
        userMsg.setContent(message);
        userMsg.setChat(chat);
        messageRepository.save(userMsg);
    }

    private void saveAssistantMessage(Chat chat, String reply) {
        Message aiMsg = new Message();
        aiMsg.setRole(Role.ASSISTANT);
        aiMsg.setContent(reply);
        aiMsg.setChat(chat);
        messageRepository.save(aiMsg);
    }

    private void saveAssistantMessage(Long chatId, String reply) {
        saveAssistantMessage(chatRepository.getReferenceById(chatId), reply);
    }

    private void applyTitle(Chat chat, String message) {
        if (chat.getTitle() == null && chat.getId() == null) {
            String title = message;
            chat.setTitle(title.length() > 50 ? title.substring(0, 47) + "..." : title);
        }
    }

    private static final int MAX_HISTORY_MESSAGES = 12;

    private String buildHistory(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        int total = messages.size();
        List<Message> recent = messages.subList(Math.max(0, total - MAX_HISTORY_MESSAGES), total);
        return recent.stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    private record Prepared(Long chatId, String history) {
    }
}
