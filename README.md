# AI Chat Bot

Spring Boot chat application backed by the Claude API.

## Tech Stack

- Java 17
- Spring Boot 3.5.16
- Gradle 9.6.1 (Groovy DSL, wrapper committed)

## Project Structure

```
src/main/java/com/example/aichatbot/
  AiChatBotApplication.java           # application entry point
  controller/ChatController.java     # calls the Claude API
  config/ClaudeConfig.java           # AnthropicClient bean
  dto/ChatRequest.java                # request/response payloads
  dto/ChatMessage.java                # one turn (role + content)
  dto/ChatResponse.java
src/main/resources/
  application.properties             # app config
  static/index.html                  # chat UI page
  static/css/chat.css
  static/js/chat.js
src/test/java/com/example/aichatbot/
  AiChatBotApplicationTests.java
  controller/ChatControllerTests.java
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

## API Endpoints

| Method | Path        | Description                                        |
|--------|-------------|------------------------------------------------------|
| POST   | /api/chat   | Sends a message to Claude and returns its reply      |

```
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"hi"}]}'
# {"reply":"...Claude's response...","timestamp":"..."}
```

The request carries the **full conversation so far** (`messages`, oldest first) so Claude has multi-turn context — the client is responsible for resending prior turns on every call; the backend is stateless and does not persist history.

## Chat UI

A chat interface is served at `http://localhost:8080/` ([index.html](src/main/resources/static/index.html)), backed by `POST /api/chat`.

Current features:
- Multiple saved conversations in a sidebar, persisted in the browser's `localStorage` — switch between them, delete one, or start a new one
- Full multi-turn history sent with every request, so Claude has context of the whole active conversation
- Message bubbles for user/bot with timestamps and auto-scroll
- Typing indicator while waiting for a reply
- Light/dark theme toggle (persisted in local storage)
- Copy-to-clipboard on messages

`ChatController` ([ChatController.java](src/main/java/com/example/aichatbot/controller/ChatController.java)) calls the Claude API via the official `anthropic-java` SDK, using model `claude-opus-4-8`. The `AnthropicClient` bean ([ClaudeConfig.java](src/main/java/com/example/aichatbot/config/ClaudeConfig.java)) reads credentials from the `ANTHROPIC_API_KEY` environment variable — never commit a key to source. A Claude API failure surfaces to the UI as an HTTP 502.

Conversations live entirely client-side (`localStorage`) — there's no server-side conversation store, no multi-device sync, and clearing browser storage deletes all chat history.

## AI-Assisted Development

This project is developed with [Claude Code](https://claude.com/claude-code) as a pair-programming assistant.

- Scaffolding (build files, application entry point, controllers, tests) and this README are generated/updated by Claude Code alongside human review.
- Every AI-generated change is reviewed and run/tested locally (or by CI) before being trusted — the assistant doesn't get unreviewed write access to production behavior.
- When asking Claude Code to extend this project, point it at this README and the existing package structure (`com.example.aichatbot`) so new code follows the same conventions.
- This section, along with the rest of the README, is kept up to date as part of each AI-assisted change.
