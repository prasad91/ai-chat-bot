package com.example.aichatbot.controller;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlockParam;
import com.example.aichatbot.document.DocumentContentBlocks;
import com.example.aichatbot.document.DocumentFetcher;
import com.example.aichatbot.document.DocumentStore;
import com.example.aichatbot.document.FetchedResource;
import com.example.aichatbot.document.StoredDocument;
import com.example.aichatbot.dto.DocumentUploadResponse;
import com.example.aichatbot.dto.DocumentUrlRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

	private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

	private static final long MAX_RESPONSE_TOKENS = 1024L;
	private static final Set<String> SUPPORTED_MEDIA_TYPES =
			Set.of("application/pdf", "text/plain", "text/markdown", "text/html");

	private final AnthropicClient anthropicClient;
	private final DocumentStore documentStore;
	private final DocumentFetcher documentFetcher;

	public DocumentController(AnthropicClient anthropicClient, DocumentStore documentStore, DocumentFetcher documentFetcher) {
		this.anthropicClient = anthropicClient;
		this.documentStore = documentStore;
		this.documentFetcher = documentFetcher;
	}

	@PostMapping
	public DocumentUploadResponse upload(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
		}

		String mediaType = resolveMediaType(file);
		if (!SUPPORTED_MEDIA_TYPES.contains(mediaType)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unsupported file type. Please upload a PDF, .txt, or .md file.");
		}

		byte[] data;
		try {
			data = file.getBytes();
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file", e);
		}

		String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document";
		StoredDocument document = new StoredDocument(UUID.randomUUID().toString(), filename, mediaType, data);
		documentStore.save(document);

		return summarize(document, filename);
	}

	@PostMapping("/url")
	public DocumentUploadResponse uploadFromUrl(@RequestBody DocumentUrlRequest request) {
		String url = request.url() == null ? "" : request.url().trim();
		if (url.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is required");
		}

		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL", e);
		}

		FetchedResource fetched = documentFetcher.fetch(uri);
		String mediaType = resolveMediaTypeFromContentType(fetched.contentType(), url);
		if (!SUPPORTED_MEDIA_TYPES.contains(mediaType)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Unsupported content type at that URL ("
							+ (fetched.contentType().isEmpty() ? "unknown" : fetched.contentType())
							+ "). Only PDF, HTML, and plain text are supported.");
		}

		String filename = deriveFilenameFromUrl(uri);
		StoredDocument document = new StoredDocument(UUID.randomUUID().toString(), filename, mediaType, fetched.data());
		documentStore.save(document);

		return summarize(document, filename);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<String> handleTooLarge(MaxUploadSizeExceededException e) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("File is too large (max 15MB).");
	}

	private DocumentUploadResponse summarize(StoredDocument document, String filename) {
		try {
			Message response = anthropicClient.messages().create(
					MessageCreateParams.builder()
							.model(Model.CLAUDE_OPUS_4_8)
							.maxTokens(MAX_RESPONSE_TOKENS)
							.addUserMessageOfBlockParams(List.of(
									DocumentContentBlocks.build(document),
									ContentBlockParam.ofText(TextBlockParam.builder()
											.text("Please summarize this document concisely so I can quickly understand its key points.")
											.build())))
							.build());

			String summary = response.content().stream()
					.flatMap(block -> block.text().stream())
					.map(textBlock -> textBlock.text())
					.reduce("", String::concat);

			return new DocumentUploadResponse(document.id(), filename, summary);
		} catch (AnthropicServiceException e) {
			log.error("Claude API call failed while summarizing document", e);
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Claude API error: " + e.getMessage(), e);
		}
	}

	private String resolveMediaType(MultipartFile file) {
		String contentType = file.getContentType();
		String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

		if ("application/pdf".equals(contentType) || filename.endsWith(".pdf")) {
			return "application/pdf";
		}
		if (filename.endsWith(".md")) {
			return "text/markdown";
		}
		if (filename.endsWith(".txt") || "text/plain".equals(contentType)) {
			return "text/plain";
		}
		return contentType != null ? contentType : "application/octet-stream";
	}

	private String resolveMediaTypeFromContentType(String contentType, String url) {
		String lowerUrl = url.toLowerCase();

		if (contentType.equalsIgnoreCase("application/pdf") || lowerUrl.endsWith(".pdf")) {
			return "application/pdf";
		}
		if (contentType.equalsIgnoreCase("text/html")) {
			return "text/html";
		}
		if (contentType.equalsIgnoreCase("text/markdown") || lowerUrl.endsWith(".md")) {
			return "text/markdown";
		}
		if (contentType.equalsIgnoreCase("text/plain") || contentType.isEmpty()) {
			return "text/plain";
		}
		return contentType;
	}

	private String deriveFilenameFromUrl(URI uri) {
		String path = uri.getPath();
		if (path != null && !path.isEmpty() && !path.equals("/")) {
			String[] segments = path.split("/");
			String last = segments[segments.length - 1];
			if (!last.isBlank()) {
				return last;
			}
		}
		return uri.getHost() != null ? uri.getHost() : uri.toString();
	}

}
