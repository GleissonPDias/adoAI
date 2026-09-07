package com.example.adoAI.exceptions;

public class AIServiceException extends RuntimeException {
    public AIServiceException(String message) {
        super(message);
    }
    public AIServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
