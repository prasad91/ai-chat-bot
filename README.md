# AI Chat Bot

[![CI](https://github.com/prasad91/ai-chat-bot/actions/workflows/ci.yml/badge.svg)](https://github.com/prasad91/ai-chat-bot/actions/workflows/ci.yml)

Spring Boot chat application backed by the Claude API.

## Tech Stack

- Java 17
- Spring Boot 3.5.16
- Gradle 9.6.1 (Groovy DSL, wrapper committed)

## Project Structure

```
src/main/java/com/example/aichatbot/
  AiChatBotApplication.java           # application entry point
  controller/ChatController.java      # calls the Claude API
  controller/DocumentController.java  # upload/URL document endpoints
  config/ClaudeConfig.java            # AnthropicClient bean
  document/StoredDocument.java        # in-memory document record
  document/DocumentStore.java         # in-memory document store
  document/DocumentContentBlocks.java # builds the Claude document content block
  document/DocumentFetcher.java       # fetches a document by URL (with SSRF guards)
  document/FetchedResource.java
  dto/ChatRequest.java                # request/response payloads
  dto/ChatMessage.java                # one turn (role + content)
  dto/ChatResponse.java
  dto/DocumentUploadResponse.java
  dto/DocumentUrlRequest.java
src/main/resources/
  application.properties             # app config
  static/index.html                  # chat UI page
  static/css/chat.css
  static/js/chat.js
src/test/java/com/example/aichatbot/
  AiChatBotApplicationTests.java
  controller/ChatControllerTests.java
  controller/ChatControllerLiveApiTests.java
  controller/DocumentControllerTests.java
  document/DocumentFetcherTests.java
```

## Prerequisites

- JDK 17
- Gradle 9.x (wrapper is committed — just use `./gradlew`)
- An Anthropic API key, exported as `ANTHROPIC_API_KEY`, to use the chat endpoint

## Running

```
export ANTHROPIC_API_KEY=sk-ant-...   # PowerShell: $env:ANTHROPIC_API_KEY = "sk-ant-..."
./gradlew bootRun
```

App starts on `http://localhost:8080`.

**Running via VS Code's Run/Debug button instead:** the Run button launches a JVM through the Java extension, which does *not* automatically inherit a Windows user environment variable set after VS Code was started — that produces a `401: x-api-key header is required` error even though `ANTHROPIC_API_KEY` is set at the OS level. To fix this without restarting VS Code every time the key changes, create a `.env` file at the project root (gitignored) with:

```
ANTHROPIC_API_KEY=sk-ant-...
```

`.vscode/launch.json` already points at it via `"envFile": "${workspaceFolder}/.env"` — just use the "AiChatBotApplication" run configuration.

## Testing

```
./gradlew test
```

- `ChatControllerTests`, `DocumentControllerTests` — fast, network-free; the `AnthropicClient` (and `DocumentFetcher`, where relevant) beans are replaced with Mockito mocks, so these always run (including in CI) without needing `ANTHROPIC_API_KEY`.
- `DocumentFetcherTests` — pure unit tests for the SSRF guards (rejects loopback/private/link-local addresses, non-http(s) schemes) — no Spring context, no network.
- `ChatControllerLiveApiTests` — opt-in integration test that calls the real Claude API. Only runs when `ANTHROPIC_API_KEY` is set (and the account has credits); skipped otherwise.

CI ([.github/workflows/ci.yml](.github/workflows/ci.yml)) runs `./gradlew build` on every push/PR to `main` via GitHub Actions — no secrets configured, so the live test is always skipped there by design.

## API Endpoints

| Method | Path                 | Description                                                   |
|--------|----------------------|-----------------------------------------------------------------|
| POST   | /api/chat            | Sends a message (+ optional document) to Claude, returns its reply |
| POST   | /api/documents       | Uploads a PDF/.txt/.md file, returns a Claude-generated summary |
| POST   | /api/documents/url   | Fetches a PDF/webpage/.txt/.md from a URL, returns a summary    |

```
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"hi"}]}'
# {"reply":"...Claude's response...","timestamp":"..."}

curl -X POST http://localhost:8080/api/documents \
  -F "file=@notes.pdf"
# {"documentId":"...","filename":"notes.pdf","summary":"..."}

curl -X POST http://localhost:8080/api/documents/url \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/report.pdf"}'
# {"documentId":"...","filename":"report.pdf","summary":"..."}
```

The chat request carries the **full conversation so far** (`messages`, oldest first) so Claude has multi-turn context — the client is responsible for resending prior turns on every call; the backend is stateless and does not persist conversation history. If a `documentId` (from either upload endpoint) is included, `ChatController` re-attaches the full document to the first user turn of every request in that conversation, with a prompt-cache breakpoint so repeat turns re-read it cheaply.

**`/api/documents/url` fetches a user-supplied URL server-side**, which is a classic SSRF (server-side request forgery) vector — `DocumentFetcher` mitigates it by rejecting non-http(s) schemes, disabling redirect-following, and rejecting hosts that resolve to loopback/private/link-local addresses (see `DocumentFetcherTests`). This is a best-effort mitigation, not foolproof against DNS-rebinding races.

## Chat UI

A chat interface is served at `http://localhost:8080/` ([index.html](src/main/resources/static/index.html)), backed by `POST /api/chat`.

Current features:
- Multiple saved conversations in a sidebar, persisted in the browser's `localStorage` — switch between them, delete one, or start a new one
- Full multi-turn history sent with every request, so Claude has context of the whole active conversation
- **Document upload** (📎) — attach a PDF, `.txt`, or `.md` file (max 15MB); Claude summarizes it immediately and the full document stays available for follow-up questions in that chat
- **Document from URL** (🔗) — paste a link to a PDF or webpage instead of uploading a file; the server fetches it and summarizes it the same way
- A document banner shows the attached file/URL under the header, with a ✕ to detach it (replacing an attached document prompts for confirmation)
- Message bubbles for user/bot with timestamps and auto-scroll
- Typing indicator while waiting for a reply
- Light/dark theme toggle (persisted in local storage)
- Copy-to-clipboard on messages

`ChatController` ([ChatController.java](src/main/java/com/example/aichatbot/controller/ChatController.java)) and `DocumentController` ([DocumentController.java](src/main/java/com/example/aichatbot/controller/DocumentController.java)) call the Claude API via the official `anthropic-java` SDK, using model `claude-opus-4-8`. The `AnthropicClient` bean ([ClaudeConfig.java](src/main/java/com/example/aichatbot/config/ClaudeConfig.java)) reads credentials from the `ANTHROPIC_API_KEY` environment variable — never commit a key to source. A Claude API failure surfaces to the UI as an HTTP 502.

Conversations live entirely client-side (`localStorage`) — there's no server-side conversation store, no multi-device sync, and clearing browser storage deletes all chat history. Uploaded/fetched documents are stored **in-memory only** ([DocumentStore.java](src/main/java/com/example/aichatbot/document/DocumentStore.java)) — they're lost on app restart and this does not scale across multiple server instances; a real deployment would need persistent or shared storage instead.

No extra Claude tools (web search, code execution) are wired in — the document Q&A works by Claude reasoning directly over the full attached document each turn, not by an agentic tool-use loop.

## AI-Assisted Development

This project is developed with [Claude Code](https://claude.com/claude-code) as a pair-programming assistant.

- Scaffolding (build files, application entry point, controllers, tests) and this README are generated/updated by Claude Code alongside human review.
- Every AI-generated change is reviewed and run/tested locally (or by CI) before being trusted — the assistant doesn't get unreviewed write access to production behavior.
- When asking Claude Code to extend this project, point it at this README and the existing package structure (`com.example.aichatbot`) so new code follows the same conventions.
- This section, along with the rest of the README, is kept up to date as part of each AI-assisted change.
