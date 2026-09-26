# 🤖 AdoAI — Chatbot com IA Generativa (Gemini)

API Web integrada com IA Generativa (Google Gemini) que implementa um **Chatbot / Assistente Virtual** para responder perguntas sobre **mangás e animes**.

Projeto desenvolvido para o curso de TSI (Senac) — ADO 1: *API Web integrada com IA Generativa*.

---

## 🗂️ Categoria

**Chatbot / Assistente Virtual** — o usuário conversa com o assistente em tempo real por meio de um chat, e a IA responde com base em um prompt estruturado com contexto e regras.

---

## ⚙️ Stack Tecnológica

| Tecnologia | Finalidade |
|---|---|
| **Java 21** | Linguagem |
| **Spring Boot 4.1.1** | Framework web |
| **Spring Web MVC** | Exposição dos endpoints REST |
| **Spring Data JPA + H2** | Persistência das conversas (banco em memória) |
| **Spring Security** | Configuração de segurança liberada para demo |
| **Jakarta Validation** | Validação de input (`@Valid`, `@NotBlank`, `@Size`) |
| **Google Gemini API** | Provedor de IA Generativa (`gemini-3.1-flash-lite`) |
| **Springdoc OpenAPI (Swagger UI)** | Documentação interativa das rotas |
| **Bucket4j** | Rate limiting (10 requisições/minuto por IP) |
| **Lombok** | Redução de boilerplate |

---

## 📦 Pré-requisitos

- **JDK 21** (testado com Zulu OpenJDK 21)
- **Maven** (ou use o wrapper `./mvnw` incluído)
- **Conta no Google AI Studio** para obter uma **API key do Gemini**

---

## 🔑 Variáveis de Ambiente

A continuação segura da chave é feita via **variável de ambiente** — a chave **nunca** deve ser commitada no repositório.

| Variável | Descrição | Obrigatória |
|---|---|---|
| `GEMINI_API_KEY` | Chave de acesso à API do Google Gemini. Obtenha em [aistudio.google.com/apikey](https://aistudio.google.com/apikey) | ✅ Sim |

### Como definir (Windows)

```powershell
setx GEMINI_API_KEY "sua-chave-aqui"
```

> **Importante:** reinicie o terminal/IDE após definir, e **NUNCA** suba a chave no GitHub.

---

## 🚀 Como Rodar o Projeto

### 1. Clone e entre na pasta

```bash
git clone <URL-do-seu-repositorio>
cd adoAI
```

### 2. Defina a variável de ambiente (veja acima)

### 3. Execute com Maven

```bash
mvn spring-boot:run
```

Ou com o wrapper:

```bash
./mvnw spring-boot:run
```

A aplicação sobe em **http://localhost:8080**.

---

## 🧪 URLs Úteis

| Recurso | URL |
|---|---|
| **Frontend (chat)** | http://localhost:8080/ |
| **Swagger UI** | http://localhost:8080/swagger-ui/index.html |
| **OpenAPI JSON** | http://localhost:8080/v3/api-docs |
| **H2 Console** | http://localhost:8080/h2-console |
| **Chat API** | http://localhost:8080/api/chat |

### Em produção (Render)

| Recurso | URL |
|---|---|
| **Frontend (chat)** | https://adoai.onrender.com/ |
| **Swagger UI** | https://adoai.onrender.com/swagger-ui/index.html |
| **OpenAPI JSON** | https://adoai.onrender.com/v3/api-docs |
| **Chat API** | https://adoai.onrender.com/api/chat |

---

## 🎨 Frontend (Chat)

O frontend fica em `src/main/resources/static/index.html` e é servido pelo próprio Spring na raiz (`/`), consumindo a API na **mesma origem** — funciona igual localmente e no Render.

Recursos:

- **Chat em bolhas** — mensagens do usuário e do assistente, com quebras de linha preservadas e texto limpo (sem Markdown cru)
- **Sidebar de conversas** — lista todas as conversas (`GET /api/chat`), permite **abrir** o histórico de uma conversa (`GET /api/chat/{id}`), **continuar** a conversa e **excluir** (`DELETE /api/chat/{id}`)
- **Feedback de carregamento** — indicador **"Digitando..."** enquanto a IA responde (requisições de IA levam alguns segundos)
- **Tratamento de erros** — mensagens legíveis quando a requisição falha (rede, timeout ou erro do provedor)

---

## 📚 Rotas da API

Todas as rotas estão sob o prefixo `/api/chat` e documentadas no Swagger.

### `POST /api/chat` — Enviar mensagem ao chatbot

Envia a pergunta do usuário, chama a IA e retorna a resposta.

**Body:**
```json
{
  "chatId": null,
  "message": "Qual a diferença entre mangá e anime?"
}
```

| Campo | Tipo | Regra |
|---|---|---|
| `chatId` | `Long` | ID da conversa. Se `null`, cria uma nova conversa |
| `message` | `String` | Obrigatória, no mín. 1 caractere, máx. 2000 |

**Resposta (201 Created):**
```json
{
  "chatId": 1,
  "reply": "Mangá é o termo para histórias em quadrinhos japonesas..."
}
```

---

### `GET /api/chat` — Listar todas as conversas

Retorna a lista de conversas salvas.

---

### `GET /api/chat/{chatId}` — Histórico de uma conversa

Retorna as mensagens (usuário e assistente) de uma conversa específica.

**Resposta (200 OK) — exemplo:**
```json
[
  {
    "id": 1,
    "role": "USER",
    "content": "Qual a diferença entre mangá e anime?",
    "createdAt": "2026-09-07T18:00:00"
  },
  {
    "id": 2,
    "role": "ASSISTANT",
    "content": "Mangá é o termo para histórias em quadrinhos...",
    "createdAt": "2026-09-07T18:00:05"
  }
]
```

---

### `DELETE /api/chat/{chatId}` — Apagar uma conversa

Remove a conversa e suas mensagens. Retorna **204 No Content**.

---

## 🛡️ Tratamento de Erros e Códigos HTTP

A API possui um **Global Exception Handler** que padroniza as respostas de erro.

| Código | Situação | Exemplo de resposta |
|---|---|---|
| **200** | Requisição bem-sucedida | Lista/histórico |
| **201** | Conversa criada / mensagem enviada | `{ chatId, reply }` |
| **204** | Delete sem conteúdo | — |
| **400** | Dados inválidos (ex: mensagem vazia, JSON malformado) | `{ status, message, fieldErrors }` |
| **404** | Conversa não encontrada | `{ status, message }` |
| **429** | Rate limit excedido (10 req/min por IP) | `{ status, message }` |
| **502** | Falha na comunicação com a IA (ex: API key inválida) | `{ status, message }` |
| **504** | Timeout do provedor de IA | `{ status, message }` |
| **500** | Erro interno não esperado | `{ status, message }` |

Formato padrão de erro:
```json
{
  "status": 400,
  "message": "Dados inválidos na requisição.",
  "timestamp": "2026-09-07T18:00:00",
  "fieldErrors": {
    "message": "A mensagem não pode estar vazia"
  }
}
```

---

## 🧠 Engenharia de Prompt e Parâmetros de Geração

O `GeminiService` monta o prompt enviado à IA com:
- **Papel definido**: "assistente virtual especializado em mangás e animes"
- **Regras de comportamento**: resposta em português brasileiro, clara e objetiva
- **Contexto**: histórico da conversa incluído no prompt
- **Anti-alucinação**: instrução explícita de não inventar respostas
- **Comparações hipotéticas** (regra 6): em perguntas como *"quem venceria X ou Y?"*, analisa os feitos documentados de cada um nos mangás/animes e defende um vencedor provável, explicando o raciocínio com base nas regras do próprio universo
- **Tom leve e humor** (regra 7): respostas com leveza quando a pergunta permitir, mantendo respeito pela informação factual
- **Sem Markdown** (regra 8): respostas em texto simples, com parágrafos curtos e quebras de linha — sem `**`, `###` ou listas com `*`

O histórico das mensagens anteriores é convertido no formato `ROLE: conteúdo` e incluído no prompt, permitindo conversas contextuais.

### Janela de contexto limitada

Para manter a coerência sem inflar o prompt, o `ChatService` envia à IA no máximo as **12 mensagens mais recentes** (~6 perguntas + 6 respostas, *sliding window*). O histórico completo permanece salvo no banco e é retornado pelo `GET /api/chat/{chatId}`.

### `generationConfig` — parâmetros de geração

| Parâmetro | Valor | Efeito |
|---|---|---|
| `temperature` | `0.8` | Mais criatividade, respostas menos padronizadas |
| `topP` | `0.95` | Amostragem por núcleo de probabilidade |
| `topK` | `40` | Restringe a amostragem aos 40 tokens mais prováveis |

---

## ⚠️ Resiliência

A API foi desenhada para aguentar falhas do provedor de IA sem "quebrar":

- **Rate limit (Bucket4j):** cada IP pode fazer **10 requisições por minuto**. Ao estourar, retorna **429** com mensagem clara — evita abuso e sobrecarga do provedor.
- **Timeout:** o `RestTemplate` tem timeout de conexão de **10s** e de leitura de **60s**. Se o Gemini demorar demais, a API responde **504** em vez de travar o servidor.
- **Retry com backoff:** em erros transitórios do provedor (**429** e **503**), a API tenta novamente até **3 vezes**, aguardando **2s** e depois **4s** antes de cada nova tentativa. Somente se todas falharem responde **502**.

---

## 🚀 Deploy no Render

O projeto já está configurado com o `Dockerfile` na raiz (multi-stage: build no `maven:3.9-eclipse-temurin-21` e runtime `eclipse-temurin:21-jre`). O `application.properties` usa `${GEMINI_API_KEY}`, que o Render resolve nativamente:

1. **Suba o código no GitHub** sem a chave.
2. No Render: **New → Web Service** → conecte o repositório.
3. Em **Environment (Docker)**, o Render detecta o `Dockerfile` automaticamente (`server.port=${PORT:8080}` já configurado).
4. Em **Environment**, adicione:
   - `GEMINI_API_KEY` → sua chave
5. **Deploy**. O app está no ar em `https://adoai.onrender.com` — o frontend na raiz (`/`) e o Swagger em `/swagger-ui/index.html`.

> **Dica de demo:** o plano gratuito do Render "dorme" após ~15 min de inatividade. A primeira requisição após o sono leva ~1 min para "acordar" (cold start). Como o banco é H2 em memória, as conversas zeram a cada reinício.

---

## 📁 Estrutura do Projeto

```
com.example.adoAI
 ┣ 📂 config        # SecurityConfig (liberado para demo)
 ┣ 📂 controllers   # ChatController (rotas REST)
 ┣ 📂 dtos          # ChatRequest, ChatResponse, ChatHistoryDTO
 ┣ 📂 entities      # Chat, Message, Role (persistência JPA)
 ┣ 📂 exceptions    # Exceções customizadas + GlobalExceptionHandler
 ┣ 📂 infrastructure# RateLimitInterceptor, WebConfig (CORS/interceptor)
 ┣ 📂 repositories  # ChatRepository, MessageRepository
 ┣ 📂 services      # GeminiService (IA), ChatService (orquestração)
 ┣ 📂 resources/static# Frontend (index.html — chat com sidebar de conversas)
```

---

## ✍️ Autor

Projeto acadêmico — ADO 1 · TSI · Senac