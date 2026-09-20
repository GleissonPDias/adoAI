package com.example.adoAI.services;

import com.example.adoAI.exceptions.AIServiceException;
import com.example.adoAI.exceptions.AITimeoutException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiService() {
        this.restTemplate = new RestTemplate();
    }

    public String generateResponse(String userMessage, String history) {
        String prompt = buildPrompt(userMessage, history);
        String responseJson = callGemini(prompt);
        return parseResponse(responseJson);
    }

    private String buildPrompt(String userMessage, String history) {
        String systemPrompt = """
                Você é um assistente virtual especializado em mangás e animes.
                Responda SEMPRE em português brasileiro, de forma clara e objetiva.
                Siga rigorosamente estas regras:
                1. Responda com base APENAS no seu conhecimento e no histórico da conversa fornecido.
                2. Você NÃO tem acesso à internet em tempo real. Seu conhecimento tem um limite de data de treinamento.
                3. Se a pergunta envolver informações que mudam com o tempo (datas futuras, resultados atuais, notícias, eventos em andamento, preços, previsões), informe claramente que depende de dados atualizados e não invente valores.
                4. Se não souber a resposta, diga claramente que não sabe, em vez de inventar.
                5. Não induza o usuário ao erro. Se teve dúvida, seja transparente sobre a incerteza.
                6. Se o usuário perguntar sobre comparações hipotéticas (ex: "quem venceria X ou Y?"), analise os feitos documentados de cada um nos mangás/animes e defenda um vencedor provável, explicando o raciocínio, com base nas regras do próprio universo.
                7. Quando a pergunta permitir, você pode responder com leveza e humor, mantendo o respeito pela informação factual.

                Histórico da conversa até agora:
                %s

                Pergunta do usuário: %s
                """.formatted(history == null || history.isBlank() ? "(sem histórico)" : history, userMessage);
        return systemPrompt;
    }

    private String callGemini(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> content = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                },
                "generationConfig", Map.of(
                        "temperature", 0.8,
                        "topP", 0.95,
                        "topK", 40,
                        "maxOutputTokens", 512
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(content, headers);

        try {
            String url = apiUrl + "?key=" + apiKey;
            byte[] responseBytes = restTemplate.postForObject(url, request, byte[].class);
            return new String(responseBytes != null ? responseBytes : new byte[0], StandardCharsets.UTF_8);
        } catch (HttpStatusCodeException e) {
            throw new AIServiceException("Erro do provedor de IA (HTTP " + e.getStatusCode().value() + "): " + e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            String cause = e.getMostSpecificCause().getMessage();
            if (cause != null && (cause.contains("timed out") || cause.contains("Read timed out"))) {
                throw new AITimeoutException("Tempo limite excedido ao aguardar resposta da IA.");
            }
            throw new AIServiceException("Falha de conexão com o provedor de IA: " + e.getMessage());
        }
    }

    private String parseResponse(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode errorNode = root.path("error");
            if (!errorNode.isEmpty()) {
                throw new AIServiceException(
                        "Erro retornado pela API do Gemini: " + errorNode.path("message").asText());
            }

            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new AIServiceException("Resposta da IA veio vazia ou sem candidatos: " + json);
            }

            JsonNode candidate = candidates.get(0);
            JsonNode parts = candidate.path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                String finishReason = candidate.path("finishReason").asText("desconhecido");
                String promptFeedback = root.path("promptFeedback").path("blockReason").asText("nenhum");
                throw new AIServiceException(
                        "Resposta da IA veio sem conteúdo. finishReason=" + finishReason
                                + ", blockReason prompt=" + promptFeedback
                                + " | body: " + json);
            }

            JsonNode firstPart = parts.get(0);
            if (firstPart == null || firstPart.isNull()) {
                throw new AIServiceException("Primeira parte da resposta da IA veio nula: " + json);
            }

            String text = firstPart.path("text").asText();
            if (text.isBlank()) {
                throw new AIServiceException("Texto vazio na resposta da IA: " + json);
            }
            return text;
        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AIServiceException(
                    "Falha ao interpretar a resposta da IA: " + e.getMessage() + " | body: " + json);
        }
    }
}