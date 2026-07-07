package com.example.aichatbot.dto;

import java.util.List;

public record ChatRequest(List<ChatMessage> messages) {
}
