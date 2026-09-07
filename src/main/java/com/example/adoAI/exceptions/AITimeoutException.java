package com.example.adoAI.exceptions;

public class AITimeoutException extends RuntimeException {
    public AITimeoutException(String message) {
        super(message);
    }
}
