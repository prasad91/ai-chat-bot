package com.example.aichatbot.controller;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.example.aichatbot.dto.ChatMessage;
import com.example.aichatbot.dto.ChatRequest;
import com.example.aichatbot.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private static final Logger log = LoggerFactory.getLogger(ChatController.class);

	private static final long MAX_RESPONSE_TOKENS = 1024L;

	private final AnthropicClient anthropicClient;

	public ChatController(AnthropicClient anthropicClient) {
		this.anthropicClient = anthropicClient;
	}

	@PostMapping
	public ChatResponse chat(@RequestBody ChatRequest request) {
		List<ChatMessage> history = request.messages() == null ? List.of() : request.messages();
		List<ChatMessage> turns = history.stream()
				.filter(turn -> turn.content() != null && !turn.content().isBlank())
				.toList();

		if (turns.isEmpty()) {
			return new ChatResponse("Say something and I'll respond!", Instant.now().toString());
		}

		MessageCreateParams.Builder paramsBuilder = MessageCreateParams.builder()
				.model(Model.CLAUDE_OPUS_4_8)
				.maxTokens(MAX_RESPONSE_TOKENS);

		for (ChatMessage turn : turns) {
			if ("assistant".equalsIgnoreCase(turn.role())) {
				paramsBuilder.addAssistantMessage(turn.content());
			} else {
				paramsBuilder.addUserMessage(turn.content());
			}
		}

		try {
			Message response = anthropicClient.messages().create(paramsBuilder.build());

			String reply = response.content().stream()
					.flatMap(block -> block.text().stream())
					.map(textBlock -> textBlock.text())
					.reduce("", String::concat);

			return new ChatResponse(reply, Instant.now().toString());
		} catch (AnthropicServiceException e) {
			log.error("Claude API call failed", e);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Claude API error: " + e.getMessage(), e);
		}
	}

}
