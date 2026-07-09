package com.example.aichatbot.document;

public record StoredDocument(String id, String filename, String mediaType, byte[] data) {
}
