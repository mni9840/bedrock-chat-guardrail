package com.example.bedrockchat.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatRequest(
        @JsonProperty("message") String message,
        @JsonProperty("systemPrompt") String systemPrompt,
        @JsonProperty("modelId") String modelId,
        @JsonProperty("maxTokens") Integer maxTokens,
        @JsonProperty("temperature") Double temperature,
        @JsonProperty("topP") Double topP,
        @JsonProperty("conversation") List<ConversationTurn> conversation) {
}

