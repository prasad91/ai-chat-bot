package com.example.aichatbot.document;

import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.DocumentBlockParam;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class DocumentContentBlocks {

	private DocumentContentBlocks() {
	}

	/**
	 * Builds the document content block sent to Claude, with a cache breakpoint so
	 * repeated turns in the same conversation re-read the document cheaply instead
	 * of reprocessing it from scratch every request.
	 */
	public static ContentBlockParam build(StoredDocument document) {
		DocumentBlockParam.Builder builder = DocumentBlockParam.builder()
				.title(document.filename())
				.cacheControl(CacheControlEphemeral.builder().build());

		if ("application/pdf".equals(document.mediaType())) {
			builder.base64Source(Base64.getEncoder().encodeToString(document.data()));
		} else {
			builder.textSource(new String(document.data(), StandardCharsets.UTF_8));
		}

		return ContentBlockParam.ofDocument(builder.build());
	}

}
