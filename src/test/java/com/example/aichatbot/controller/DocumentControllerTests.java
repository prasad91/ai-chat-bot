package com.example.aichatbot.controller;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.services.blocking.MessageService;
import com.example.aichatbot.document.DocumentFetcher;
import com.example.aichatbot.document.FetchedResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fast, network-free tests. The AnthropicClient bean is replaced with a Mockito mock,
 * so these run in CI without ANTHROPIC_API_KEY.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DocumentControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AnthropicClient anthropicClient;

	@MockitoBean
	private DocumentFetcher documentFetcher;

	@Test
	void uploadRejectsUnsupportedFileType() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "fake-bytes".getBytes());

		mockMvc.perform(multipart("/api/documents").file(file))
				.andExpect(status().isBadRequest());
	}

	@Test
	void uploadSummarizesTextDocument() throws Exception {
		MessageService messageService = mock(MessageService.class);
		when(anthropicClient.messages()).thenReturn(messageService);

		TextBlock textBlock = TextBlock.builder()
				.text("This document describes a simple test fixture.")
				.citations(List.of())
				.build();
		Message mockMessage = mock(Message.class);
		when(mockMessage.content()).thenReturn(List.of(ContentBlock.ofText(textBlock)));
		when(messageService.create(any(MessageCreateParams.class))).thenReturn(mockMessage);

		MockMultipartFile file = new MockMultipartFile(
				"file", "notes.txt", "text/plain", "Hello, this is a test document.".getBytes());

		mockMvc.perform(multipart("/api/documents").file(file))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.filename").value("notes.txt"))
				.andExpect(jsonPath("$.summary").value("This document describes a simple test fixture."))
				.andExpect(jsonPath("$.documentId").isNotEmpty());
	}

	@Test
	void uploadFromUrlSummarizesFetchedDocument() throws Exception {
		when(documentFetcher.fetch(any(URI.class)))
				.thenReturn(new FetchedResource("Hello from the web".getBytes(StandardCharsets.UTF_8), "text/plain"));

		MessageService messageService = mock(MessageService.class);
		when(anthropicClient.messages()).thenReturn(messageService);

		TextBlock textBlock = TextBlock.builder()
				.text("Summary of the fetched page.")
				.citations(List.of())
				.build();
		Message mockMessage = mock(Message.class);
		when(mockMessage.content()).thenReturn(List.of(ContentBlock.ofText(textBlock)));
		when(messageService.create(any(MessageCreateParams.class))).thenReturn(mockMessage);

		mockMvc.perform(post("/api/documents/url")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"url\":\"https://example.com/notes.txt\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.filename").value("notes.txt"))
				.andExpect(jsonPath("$.summary").value("Summary of the fetched page."));
	}

	@Test
	void uploadFromUrlRejectsUnsupportedContentType() throws Exception {
		when(documentFetcher.fetch(any(URI.class)))
				.thenReturn(new FetchedResource("binary-data".getBytes(StandardCharsets.UTF_8), "image/png"));

		mockMvc.perform(post("/api/documents/url")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"url\":\"https://example.com/photo.png\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void uploadFromUrlRejectsBlankUrl() throws Exception {
		mockMvc.perform(post("/api/documents/url")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"url\":\"\"}"))
				.andExpect(status().isBadRequest());
	}

}
