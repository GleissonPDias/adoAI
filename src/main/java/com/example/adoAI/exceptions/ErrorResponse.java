package com.example.adoAI.exceptions;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

@Schema(description = "Padrão de resposta de erro da API")
public class ErrorResponse {

    @Schema(description = "Código HTTP do erro", example = "400")
    private int status;

    @Schema(description = "Mensagem legível do erro", example = "A mensagem não pode estar vazia")
    private String message;

    @Schema(description = "Momento em que o erro ocorreu")
    private LocalDateTime timestamp;

    private Map<String, String> fieldErrors;

    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(int status, String message, Map<String, String> fieldErrors) {
        this.status = status;
        this.message = message;
        this.timestamp = LocalDateTime.now();
        this.fieldErrors = fieldErrors;
    }

}
