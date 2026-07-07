package com.example.aichatbot.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Opt-in integration test that calls the real Claude API. Only runs when
 * ANTHROPIC_API_KEY is set (and the account has credits) — skipped otherwise,
 * including in CI, so it never blocks a build on network/billing state.
 */
@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ChatControllerLiveApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void chatCallsRealClaudeWithConversationHistory() throws Exception {
		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"messages\":["
								+ "{\"role\":\"user\",\"content\":\"My favorite number is 7. Just say OK.\"},"
								+ "{\"role\":\"assistant\",\"content\":\"OK.\"},"
								+ "{\"role\":\"user\",\"content\":\"What is my favorite number? Reply with just the digit.\"}"
								+ "]}"))
				.andExpect(status().isOk());
	}

}
