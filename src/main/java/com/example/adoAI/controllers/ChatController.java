package com.example.adoAI.controllers;


import com.example.adoAI.dtos.ChatHistoryDTO;
import com.example.adoAI.dtos.ChatRequest;
import com.example.adoAI.dtos.ChatResponse;
import com.example.adoAI.entities.Chat;
import com.example.adoAI.services.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Endpoints do chatbot com IA generativa")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @Operation(summary = "Envia uma mensagem para o chatbot e retorna a resposta da IA")
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody ChatRequest request ) {
        ChatResponse response = chatService.sendMessage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{chatId}")
    @Operation(summary = "Retorna o histórico de uma conversa")
    public ResponseEntity<List<ChatHistoryDTO>> getChatHistory(@PathVariable Long chatId) {
        List<ChatHistoryDTO> history = chatService.getChatHistory(chatId);
        return ResponseEntity.ok(history);
    }

    @GetMapping
    @Operation(summary = "Lista todas as conversas")
    public ResponseEntity<List<Chat>> getAllChats() {
        List<Chat> chats = chatService.getAllChats();
        return ResponseEntity.ok(chats);
    }

    @DeleteMapping("/{chatId}")
    @Operation(summary = "Apaga uma conversa")
    public ResponseEntity<Void> deleteChat(@PathVariable Long chatId){
        chatService.deleteChat(chatId);
        return ResponseEntity.noContent().build();
    }

}
