package com.example.adoAI.dtos;


import com.example.adoAI.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Uma única mensagem dentro de um histórico de conversa")
public class ChatHistoryDTO {

    private Long id;
    private Role role;
    private String content;
    private LocalDateTime createdAt;
}
