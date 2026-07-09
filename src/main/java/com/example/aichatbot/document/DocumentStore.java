package com.example.aichatbot.document;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory only: documents are lost on restart and this does not scale across
 * multiple instances. Fine for a demo; a real deployment would need a persistent
 * or shared store (database, object storage) instead.
 */
@Component
public class DocumentStore {

	private final Map<String, StoredDocument> documents = new ConcurrentHashMap<>();

	public void save(StoredDocument document) {
		documents.put(document.id(), document);
	}

	public StoredDocument get(String id) {
		return documents.get(id);
	}

}
