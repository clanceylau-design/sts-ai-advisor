package com.stsaiadvisor.llm;

import com.stsaiadvisor.config.ModConfig;

/**
 * Factory for creating LLM clients based on configuration.
 */
public class LLMClientFactory {

    /**
     * Create an LLM client based on the configuration.
     */
    public static LLMClient createClient(ModConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }

        String provider = config.getApiProvider();
        if (provider == null) {
            provider = "anthropic";
        }

        switch (provider.toLowerCase()) {
            case "openai":
                System.out.println("[AI Advisor] Using OpenAI client with model: " + config.getModel());
                return new OpenAIClient(config);
            case "anthropic":
            default:
                System.out.println("[AI Advisor] Using Claude client with model: " + config.getModel());
                return new ClaudeClient(config);
        }
    }
}