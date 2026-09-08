package com.example.adoAI.services;

import com.example.adoAI.exceptions.AIServiceException;
import com.example.adoAI.exceptions.AITimeoutException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
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
        // Força UTF-8 para decodificar corretamente a resposta da IA
        this.restTemplate.getMessageConverters()
                .forEach(converter -> {
                    if (converter instanceof StringHttpMessageConverter) {
                        ((StringHttpMessageConverter) converter).setDefaultCharset(StandardCharsets.UTF_8);
                    }
                });
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
                Use apenas o contexto e o histórico fornecidos.
                Se não souber a resposta, diga claramente que não sabe, em vez de inventar.

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
                }
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(content, headers);

        try {
            String url = apiUrl + "?key=" + apiKey;
            return restTemplate.postForObject(url, request, String.class);
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
            JsonNode candidates = root.path("candidates");
            if (candidates.isEmpty()) {
                throw new AIServiceException("Resposta da IA veio vazia ou sem candidatos.");
            }
            JsonNode text = candidates.get(0).path("content").path("parts").get(0).path("text");
            return text.asText();
        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AIServiceException("Falha ao interpretar a resposta da IA: " + e.getMessage());
        }
    }
}