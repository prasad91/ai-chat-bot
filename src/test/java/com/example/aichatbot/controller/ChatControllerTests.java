package com.example.aichatbot.controller;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.services.blocking.MessageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast, network-free tests. The AnthropicClient bean is replaced with a Mockito mock,
 * so these run in CI without ANTHROPIC_API_KEY. See ChatControllerLiveApiTests for the
 * opt-in test that hits the real Claude API.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AnthropicClient anthropicClient;

	@Test
	void chatHandlesEmptyHistory() throws Exception {
		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"messages\":[]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply").value("Say something and I'll respond!"));
	}

	@Test
	void chatReturnsClaudeReplyForConversationHistory() throws Exception {
		MessageService messageService = mock(MessageService.class);
		when(anthropicClient.messages()).thenReturn(messageService);

		TextBlock textBlock = TextBlock.builder()
				.text("Mocked reply from Claude")
				.citations(List.of())
				.build();
		Message mockMessage = mock(Message.class);
		when(mockMessage.content()).thenReturn(List.of(ContentBlock.ofText(textBlock)));
		when(messageService.create(any(MessageCreateParams.class))).thenReturn(mockMessage);

		mockMvc.perform(post("/api/chat")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"messages\":["
								+ "{\"role\":\"user\",\"content\":\"My favorite number is 7. Just say OK.\"},"
								+ "{\"role\":\"assistant\",\"content\":\"OK.\"},"
								+ "{\"role\":\"user\",\"content\":\"What is my favorite number?\"}"
								+ "]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reply").value("Mocked reply from Claude"));
	}

}
