package com.example.aichatbot.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void chatHandlesEmptyHistory() throws Exception {
		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"messages\":[]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply").value("Say something and I'll respond!"));
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
	void chatCallsClaudeWithConversationHistory() throws Exception {
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
