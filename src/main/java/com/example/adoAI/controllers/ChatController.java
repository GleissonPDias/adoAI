package com.example.adoAI.controllers;


import com.example.adoAI.dtos.ChatHistoryDTO;
import com.example.adoAI.dtos.ChatRequest;
import com.example.adoAI.dtos.ChatResponse;
import com.example.adoAI.entities.Chat;
import com.example.adoAI.exceptions.ErrorResponse;
import com.example.adoAI.services.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Chat", description = "Endpoints do chatbot com IA generativa")
@RestController
@RequestMapping("/api/chat")
@ApiResponse(responseCode = "429", description = "Muitas requisições no minuto (rate limit de 10 requisições/minuto por IP)",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "429", summary = "Rate limit excedido",
                        value = "{\"status\":429,\"message\":\"Muitas requisições. Aguarde um momento e tente novamente.\",\"timestamp\":\"2026-09-27T21:49:59.765Z\"}")))
@ApiResponse(responseCode = "500", description = "Erro interno inesperado",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(name = "500", summary = "Erro interno",
                        value = "{\"status\":500,\"message\":\"Erro interno do servidor. Tente novamente mais tarde.\",\"timestamp\":\"2026-09-27T21:49:59.765Z\"}")))
public class ChatController {

    private static final String TS = "2026-09-27T21:49:59.765Z";

    private static final String E400_EMPTY = "{\"status\":400,\"message\":\"Dados inválidos na requisição.\"," +
            "\"timestamp\":\"" + TS + "\",\"fieldErrors\":{\"message\":\"A mensagem não pode estar vazia ou ser apenas espaços\"}}";

    private static final String E400_ID = "{\"status\":400,\"message\":\"Parâmetro inválido: 'chatId' deve ser um número inteiro.\"," +
            "\"timestamp\":\"" + TS + "\"}";

    private static final String E404_CHAT = "{\"status\":404,\"message\":\"Conversa não encontrada com o ID: 99999\"," +
            "\"timestamp\":\"" + TS + "\"}";

    private static final String E502 = "{\"status\":502,\"message\":\"Falha ao se comunicar com o provedor de IA: Erro do provedor de IA (HTTP 500).\"," +
            "\"timestamp\":\"" + TS + "\"}";

    private static final String E504 = "{\"status\":504,\"message\":\"O provedor de IA demorou demais para responder: Tempo limite excedido ao aguardar resposta da IA.\"," +
            "\"timestamp\":\"" + TS + "\"}";

    private static final String EX_201 = "{\"chatId\":1,\"reply\":\"Mangá é o termo para histórias em quadrinhos japonesas...\"}";

    private static final String EX_HISTORY = "[{\"id\":1,\"role\":\"USER\",\"content\":\"Mangá ou anime?\",\"createdAt\":\"" + TS + "\"}," +
            "{\"id\":2,\"role\":\"ASSISTANT\",\"content\":\"Mangá é o termo para histórias em quadrinhos japonesas...\",\"createdAt\":\"" + TS + "\"}]";

    private static final String EX_LIST = "[{\"id\":1,\"title\":\"Mangá ou anime?\",\"createdAt\":\"" + TS + "\",\"updatedAt\":\"" + TS + "\"}]";

    private static final String REQ_EX = "{\"chatId\":null,\"message\":\"Qual a diferença entre mangá e anime?\"}";

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @Operation(summary = "Envia uma mensagem para o chatbot e retorna a resposta da IA",
            description = "Cria uma nova conversa quando chatId é nulo ou continua a conversa existente (enviando o histórico recente à IA). " +
                    "O chatbot responde com base no prompt estruturado e no contexto da conversa.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Conversa processada e resposta da IA gerada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatResponse.class),
                            examples = @ExampleObject(name = "201", summary = "Resposta gerada", value = EX_201))),
            @ApiResponse(responseCode = "400",
                    description = "Requisição inválida: mensagem vazia, excedendo 2000 caracteres, corpo vazio ou JSON malformado",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "400", summary = "Mensagem vazia", value = E400_EMPTY))),
            @ApiResponse(responseCode = "502", description = "Falha na comunicação com o provedor de IA após todas as tentativas",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "502", summary = "Provedor de IA com erro", value = E502))),
            @ApiResponse(responseCode = "504", description = "O provedor de IA demorou além do tempo limite",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "504", summary = "Timeout do provedor de IA", value = E504)))
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Mensagem do usuário. informe 'chatId' para continuar uma conversa existente ou deixe nulo para criar uma nova.",
            required = true,
            content = @Content(mediaType = "application/json",
                    examples = @ExampleObject(name = "body", summary = "Exemplo de envio", value = REQ_EX)))
    @PostMapping
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody ChatRequest request ) {
        ChatResponse response = chatService.sendMessage(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Retorna o histórico de uma conversa",
            description = "Lista as mensagens (usuário e assistente) de uma conversa específica, em ordem cronológica.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Histórico de mensagens da conversa",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChatHistoryDTO.class),
                            examples = @ExampleObject(name = "200", summary = "Histórico da conversa", value = EX_HISTORY))),
            @ApiResponse(responseCode = "400", description = "Parâmetro chatId inválido (deve ser um número)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "400", summary = "ID inválido", value = E400_ID))),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "404", summary = "Conversa inexistente", value = E404_CHAT)))
    })
    @GetMapping("/{chatId}")
    public ResponseEntity<List<ChatHistoryDTO>> getChatHistory(@PathVariable Long chatId) {
        List<ChatHistoryDTO> history = chatService.getChatHistory(chatId);
        return ResponseEntity.ok(history);
    }

    @Operation(summary = "Lista todas as conversas",
            description = "Retorna as conversas salvas (id, título e datas) para popular a lista lateral do chat.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista de conversas salvas",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Chat.class),
                            examples = @ExampleObject(name = "200", summary = "Lista de conversas", value = EX_LIST)))
    })
    @GetMapping
    public ResponseEntity<List<Chat>> getAllChats() {
        List<Chat> chats = chatService.getAllChats();
        return ResponseEntity.ok(chats);
    }

    @Operation(summary = "Apaga uma conversa",
            description = "Remove permanentemente a conversa e todas as suas mensagens.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Conversa apagada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetro chatId inválido (deve ser um número)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "400", summary = "ID inválido", value = E400_ID))),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "404", summary = "Conversa inexistente", value = E404_CHAT)))
    })
    @DeleteMapping("/{chatId}")
    public ResponseEntity<Void> deleteChat(@PathVariable Long chatId){
        chatService.deleteChat(chatId);
        return ResponseEntity.noContent().build();
    }

}