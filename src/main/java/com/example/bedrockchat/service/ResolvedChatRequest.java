package com.example.bedrockchat.service;

import com.example.bedrockchat.model.ConversationTurn;

import java.util.List;

public record ResolvedChatRequest(
        String modelId,
        String systemPrompt,
        String message,
        List<ConversationTurn> conversation,
        int maxTokens,
        double temperature,
        double topP) {
}

