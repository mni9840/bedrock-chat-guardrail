package com.example.bedrockchat.service;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.example.bedrockchat.config.AppConfig;
import com.example.bedrockchat.model.ChatRequest;
import com.example.bedrockchat.model.ChatResponse;
import com.example.bedrockchat.model.ConversationTurn;

import java.util.List;
import java.util.Locale;

public class BedrockChatService {

    private static final int MIN_MAX_TOKENS = 1;
    private static final int MAX_MAX_TOKENS = 4096;
    private final AppConfig appConfig;
    private final BedrockChatClient bedrockChatClient;

    public BedrockChatService(AppConfig appConfig, BedrockChatClient bedrockChatClient) {
        this.appConfig = appConfig;
        this.bedrockChatClient = bedrockChatClient;
    }

    public AppConfig getAppConfig() {
        return appConfig;
    }

    public ChatResponse chat(ChatRequest request, String requestId, LambdaLogger logger) {
        ResolvedChatRequest resolvedRequest = resolve(request, logger);
        logger.log("resolvedRequest"+resolvedRequest);
        ChatCompletionResult completionResult = bedrockChatClient.converse(resolvedRequest);
        logger.log("ChatCompletionResult"+completionResult);

        return ChatResponse.success(
                requestId,
                completionResult.reply(),
                completionResult.modelId(),
                completionResult.stopReason(),
                completionResult.inputTokens(),
                completionResult.outputTokens());
    }

    ResolvedChatRequest resolve(ChatRequest request, LambdaLogger logger) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }

        String message = normalizeText(request.message(), "Field 'message' is required.");
        String systemPrompt = normalizeNullableText(request.systemPrompt());
        String modelId = normalizeNullableText(request.modelId() == null ? appConfig.defaultModelId() : request.modelId());
        int maxTokens = request.maxTokens() == null ? appConfig.defaultMaxTokens() : request.maxTokens();
        double temperature = request.temperature() == null ? appConfig.defaultTemperature() : request.temperature();
        double topP = request.topP() == null ? appConfig.defaultTopP() : request.topP();
        List<ConversationTurn> conversation = request.conversation() == null ? List.of() : request.conversation();

        logger.log("Normalization completed");

        if (maxTokens < MIN_MAX_TOKENS || maxTokens > MAX_MAX_TOKENS) {
            throw new IllegalArgumentException("Field 'maxTokens' must be between 1 and 4096.");
        }
        if (temperature < 0.0d || temperature > 1.0d) {
            throw new IllegalArgumentException("Field 'temperature' must be between 0.0 and 1.0.");
        }
        if (topP <= 0.0d || topP > 1.0d) {
            throw new IllegalArgumentException("Field 'topP' must be greater than 0.0 and less than or equal to 1.0.");
        }

        validateConversation(conversation);
        logger.log("validateConversation completed");

        ResolvedChatRequest resolvedChatRequest= new ResolvedChatRequest(
                modelId == null ? appConfig.defaultModelId() : modelId,
                systemPrompt,
                message,
                conversation,
                maxTokens,
                temperature,
                topP);
        logger.log("resolvedChatRequest: "+resolvedChatRequest);
        return resolvedChatRequest;
    }

    private static void validateConversation(List<ConversationTurn> conversation) {
        for (ConversationTurn turn : conversation) {
            if (turn == null) {
                throw new IllegalArgumentException("Conversation entries cannot be null.");
            }
            String role = normalizeText(turn.role(), "Conversation role is required.").toLowerCase(Locale.ROOT);
            if (!role.equals("user") && !role.equals("assistant")) {
                throw new IllegalArgumentException("Conversation role must be 'user' or 'assistant'.");
            }
            normalizeText(turn.message(), "Conversation message is required.");
        }
    }

    private static String normalizeText(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }

    private static String normalizeNullableText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}


