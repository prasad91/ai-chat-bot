package com.example.aichatbot.document;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests (no Spring context, no network) for the SSRF guards in
 * DocumentFetcher. Each case must be rejected before any HTTP call is attempted.
 */
class DocumentFetcherTests {

	private final DocumentFetcher documentFetcher = new DocumentFetcher();

	@Test
	void rejectsNonHttpScheme() {
		assertThrows(ResponseStatusException.class, () -> documentFetcher.fetch(URI.create("file:///etc/passwd")));
	}

	@Test
	void rejectsLoopbackAddress() {
		assertThrows(ResponseStatusException.class, () -> documentFetcher.fetch(URI.create("http://127.0.0.1/secret")));
	}

	@Test
	void rejectsLocalhostHostname() {
		assertThrows(ResponseStatusException.class, () -> documentFetcher.fetch(URI.create("http://localhost:8080/actuator/env")));
	}

	@Test
	void rejectsLinkLocalCloudMetadataAddress() {
		assertThrows(ResponseStatusException.class,
				() -> documentFetcher.fetch(URI.create("http://169.254.169.254/latest/meta-data/")));
	}

	@Test
	void rejectsPrivateNetworkAddress() {
		assertThrows(ResponseStatusException.class, () -> documentFetcher.fetch(URI.create("http://192.168.1.1/")));
	}

}
