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
| **Swagger UI** | http://localhost:8080/swagger-ui/index.html |
| **OpenAPI JSON** | http://localhost:8080/v3/api-docs |
| **H2 Console** | http://localhost:8080/h2-console |
| **Chat API** | http://localhost:8080/api/chat |

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

## 🧠 Engenharia de Prompt

O `GeminiService` monta o prompt enviado à IA com:
- **Papel definido**: "assistente virtual especializado em mangás e animes"
- **Regras de comportamento**: resposta em português brasileiro, clara e objetiva
- **Contexto**: histórico da conversa incluído no prompt
- **Anti-alucinação**: instrução explícita de não inventar respostas

O histórico das mensagens anteriores é convertido no formato `ROLE: conteúdo` e incluído no prompt, permitindo conversas contextuais.

---

## ⚠️ Rate Limit (Resiliência)

Implementado com **Bucket4j**: cada IP pode fazer **10 requisições por minuto**. Ao estourar, a API retorna **429** com mensagem clara — protegendo a aplicação contra abuso e sobrecarga do provedor de IA.

---

## 🚀 Deploy no Render

O projeto está pronto para deploy no [Render](https://render.com) (o `application.properties` usa `${GEMINI_API_KEY}`, que o Render resolve nativamente):

1. **Suba o código no GitHub** sem a chave.
2. No Render: **New → Web Service** → conecte o repositório.
3. Configuração:
   - **Build Command**: `./mvnw clean package -DskipTests`
   - **Start Command**: `java -jar target/adoAI-0.0.1-SNAPSHOT.jar`
4. Em **Environment**, adicione:
   - `GEMINI_API_KEY` → sua chave
5. **Deploy**. A API ficará disponível em `https://<seu-servico>.onrender.com` com o Swagger em `/swagger-ui/index.html`.

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
 ┗ 📂 services      # GeminiService (IA), ChatService (orquestração)
```

---

## ✍️ Autor

Projeto acadêmico — ADO 1 · TSI · Senac