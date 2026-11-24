package com.example.adoAI.services;

import com.example.adoAI.exceptions.AIServiceException;
import com.example.adoAI.exceptions.AITimeoutException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final int MAX_RETRIES = 3;
    private static final int CONNECT_TIMEOUT_SECONDS = 10;
    private static final int READ_TIMEOUT_SECONDS = 60;
    private static final int RETRY_BACKOFF_SECONDS = 2;

    public GeminiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_SECONDS * 1000);
        factory.setReadTimeout(READ_TIMEOUT_SECONDS * 1000);
        this.restTemplate = new RestTemplate(factory);
        this.webClient = WebClient.create();
    }

    /**
     * Fluxo bloqueante existente (mantido para compatibilidade da API).
     */
    public String generateResponse(String userMessage, String history) {
        String prompt = buildPrompt(userMessage, history);
        String responseJson = callGemini(prompt);
        return parseResponse(responseJson);
    }

    /**
     * Streaming real: consome o endpoint nativo streamGenerateContent do Gemini (alt=sse)
     * e emite cada delta de texto assim que ele chega, sem esperar a resposta completa.
     */
    public Flux<String> streamResponse(String userMessage, String history) {
        String prompt = buildPrompt(userMessage, history);

        return webClient.post()
                .uri(buildStreamUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(buildRequestBody(prompt))
                .retrieve()
                .bodyToFlux(String.class)
                // timeout de ociosidade entre chunks (reinicia a cada evento recebido)
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                // 429/503 ocorrem antes de qualquer chunk: retry não duplica texto já enviado
                .retryWhen(Retry.backoff(MAX_RETRIES - 1L, Duration.ofSeconds(RETRY_BACKOFF_SECONDS))
                        .filter(GeminiService::isRetryable)
                        .jitter(0.0))
                .onErrorMap(TimeoutException.class, ex ->
                        new AITimeoutException("Tempo limite excedido ao aguardar resposta da IA."))
                .onErrorMap(WebClientResponseException.class, ex ->
                        new AIServiceException("Erro do provedor de IA (HTTP " + ex.getStatusCode().value()
                                + "): " + ex.getResponseBodyAsString()))
                .onErrorMap(WebClientRequestException.class, ex ->
                        new AIServiceException("Falha de conexão com o provedor de IA: " + ex.getMessage()))
                .map(this::parseStreamChunk)
                .filter(text -> !text.isEmpty());
    }

    private static boolean isRetryable(Throwable ex) {
        if (ex instanceof WebClientResponseException we) {
            int status = we.getStatusCode().value();
            return status == 429 || status == 503;
        }
        return false;
    }

    private String buildStreamUrl() {
        String url = apiUrl.replace(":generateContent", ":streamGenerateContent");
        return url + (url.contains("?") ? "&" : "?") + "alt=sse&key=" + apiKey;
    }

    private String buildPrompt(String userMessage, String history) {
        String systemPrompt = """
                Você é um assistente virtual especializado estritamente em mangás, quadrinhos, animes e jogos relacionado a esses temas, portanto perguntas fora desse escopo você não deve responder.
                Responda SEMPRE em português brasileiro, de forma clara e objetiva.
                Siga rigorosamente estas regras:
                1. Responda com base APENAS no seu conhecimento e no histórico da conversa fornecido.
                2. Você NÃO tem acesso à internet em tempo real. Seu conhecimento tem um limite de data de treinamento.
                3. Se a pergunta envolver informações que mudam com o tempo (datas futuras, resultados atuais, notícias, eventos em andamento, preços, previsões), informe claramente que depende de dados atualizados e não invente valores.
                4. Se não souber a resposta, diga claramente que não sabe, em vez de inventar.
                5. Não induza o usuário ao erro. Se teve dúvida, seja transparente sobre a incerteza.
                6. Se o usuário perguntar sobre comparações hipotéticas (ex: "quem venceria X ou Y?"), analise os feitos documentados de cada um nos mangás/animes e defenda um vencedor provável, explicando o raciocínio, com base nas regras do próprio universo.
                7. Quando a pergunta permitir, você pode responder com leveza e humor, mantendo o respeito pela informação factual.
                8. Não use formatação Markdown (negrito **, títulos ###, linhas ---, listas com *). Responda em texto simples, com parágrafos curtos e quebra de linha entre tópicos.

                Histórico da conversa até agora:
                %s

                Pergunta do usuário: %s
                """.formatted(history == null || history.isBlank() ? "(sem histórico)" : history, userMessage);
        return systemPrompt;
    }

    private Map<String, Object> buildRequestBody(String prompt) {
        return Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", prompt)
                        })
                },
                "generationConfig", Map.of(
                        "temperature", 0.8,
                        "topP", 0.95,
                        "topK", 40
                )
        );
    }

    private String callGemini(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(buildRequestBody(prompt), headers);
        String url = apiUrl + "?key=" + apiKey;

        for (int attempt = 1; ; attempt++) {
            try {
                byte[] responseBytes = restTemplate.postForObject(url, request, byte[].class);
                return new String(responseBytes != null ? responseBytes : new byte[0], StandardCharsets.UTF_8);
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                if ((status == 429 || status == 503) && attempt < MAX_RETRIES) {
                    sleepBeforeRetry(attempt);
                    continue;
                }
                throw new AIServiceException("Erro do provedor de IA (HTTP " + status + "): " + e.getResponseBodyAsString());
            } catch (ResourceAccessException e) {
                String cause = e.getMostSpecificCause().getMessage();
                if (cause != null && (cause.contains("timed out") || cause.contains("Read timed out"))) {
                    throw new AITimeoutException("Tempo limite excedido ao aguardar resposta da IA.");
                }
                throw new AIServiceException("Falha de conexão com o provedor de IA: " + e.getMessage());
            }
        }
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(attempt * RETRY_BACKOFF_SECONDS * 1000L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new AIServiceException("Requisição interrompida durante a espera para nova tentativa.");
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

    /**
     * Cada evento SSE do Gemini é um GenerateContentResponse completo com um pedaço do texto.
     */
    private String parseStreamChunk(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode errorNode = root.path("error");
            if (!errorNode.isEmpty()) {
                throw new AIServiceException(
                        "Erro retornado pela API do Gemini: " + errorNode.path("message").asText());
            }

            JsonNode candidates = root.path("candidates");
            if (!candidates.isArray() || candidates.isEmpty()) {
                return "";
            }

            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                return "";
            }

            StringBuilder text = new StringBuilder();
            for (JsonNode part : parts) {
                if (part.path("thought").asBoolean(false)) {
                    continue;
                }
                text.append(part.path("text").asText(""));
            }
            return text.toString();
        } catch (AIServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AIServiceException(
                    "Falha ao interpretar o stream da IA: " + e.getMessage() + " | chunk: " + json);
        }
    }
}
