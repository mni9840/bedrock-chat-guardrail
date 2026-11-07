package com.example.bedrockchat.service;

public record ChatCompletionResult(
        String reply,
        String modelId,
        String stopReason,
        int inputTokens,
        int outputTokens) {
}

