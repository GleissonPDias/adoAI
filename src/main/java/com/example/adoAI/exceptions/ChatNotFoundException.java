package com.example.adoAI.exceptions;

public class ChatNotFoundException extends RuntimeException {
    public ChatNotFoundException(Long id) {
        super("Conversa não encontrada com o ID: " + id);
    }
}
