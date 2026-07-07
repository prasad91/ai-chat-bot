package com.example.aichatbot.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClaudeConfig {

	@Bean
	public AnthropicClient anthropicClient() {
		return AnthropicOkHttpClient.fromEnv();
	}

}
