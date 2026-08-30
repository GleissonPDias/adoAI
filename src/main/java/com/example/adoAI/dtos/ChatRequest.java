package com.example.adoAI.dtos;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Schema(description = "Requisição de envio de mensagem para o chatbot")
public class ChatRequest {
    @Schema(description = "ID da conversa. Se nulo, uma nova conversa será criada", example = "1")
    private Long chatId;


    @NotBlank(message = "A mensagem não pode estar vazia ou ser apenas espaços")
    @Size(max=2000, message = "A mensagem não pode exceder 2000 caracteres")
    @Schema(description = "Mensagem/pergunta enviada pelo usuário", example = "Qual o mangá mais popular?")
    private String message;

}
