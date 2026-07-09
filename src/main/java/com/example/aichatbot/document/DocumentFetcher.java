package com.example.aichatbot.document;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches a document from a user-supplied URL. Since this makes the server issue
 * an outbound request to an address the client controls, it validates against SSRF:
 * only http(s) schemes, no redirects (a redirect could point past the check to an
 * internal address), and the resolved host must not be a loopback/private/link-local
 * address. This is a best-effort mitigation, not foolproof against DNS-rebinding
 * (the resolution here and the one HttpClient performs on send are not atomic).
 */
@Component
public class DocumentFetcher {

	private static final long MAX_BYTES = 15L * 1024 * 1024;

	private final HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NEVER)
			.build();

	public FetchedResource fetch(URI uri) {
		validateScheme(uri);
		validateNotPrivateAddress(uri);

		HttpResponse<byte[]> response;
		try {
			HttpRequest request = HttpRequest.newBuilder(uri)
					.timeout(Duration.ofSeconds(20))
					.GET()
					.build();
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not fetch the URL: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Fetching the URL was interrupted", e);
		}

		if (response.statusCode() >= 300) {
			String hint = response.statusCode() < 400
					? " (redirects are not followed; paste the final URL directly)"
					: "";
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
					"Fetching the URL returned status " + response.statusCode() + hint);
		}

		byte[] data = response.body();
		if (data.length > MAX_BYTES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Document at that URL is too large (max 15MB).");
		}

		String contentType = response.headers().firstValue("Content-Type")
				.map(value -> value.split(";")[0].trim())
				.orElse("");
		return new FetchedResource(data, contentType);
	}

	private void validateScheme(URI uri) {
		String scheme = uri.getScheme();
		if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must start with http:// or https://");
		}
	}

	private void validateNotPrivateAddress(URI uri) {
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid URL");
		}

		InetAddress address;
		try {
			address = InetAddress.getByName(host);
		} catch (UnknownHostException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not resolve host: " + host);
		}

		if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
				|| address.isAnyLocalAddress() || address.isMulticastAddress()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"URLs pointing to private or internal addresses are not allowed");
		}
	}

}
