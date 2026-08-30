package com.example.adoAI.dtos;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Resposta do chatbot após processar a mensagem")
public class ChatResponse {

    @Schema(description = "ID da conversa", example = "1")
    private Long chatId;

    @Schema(description = "Resposta gerada pela IA", example = "O mangá mais popular é One Piece...")
    private String reply;
}
