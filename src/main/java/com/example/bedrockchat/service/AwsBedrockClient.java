package com.example.bedrockchat.service;

import com.example.bedrockchat.config.AppConfig;
import com.example.bedrockchat.model.ConversationTurn;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class AwsBedrockClient implements BedrockChatClient {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final AppConfig appConfig;

    public AwsBedrockClient(AppConfig appConfig) {
        this(appConfig, BedrockRuntimeClient.builder()
                .region(appConfig.region())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build());
    }

    AwsBedrockClient(AppConfig appConfig, BedrockRuntimeClient bedrockRuntimeClient) {
        this.appConfig = appConfig;
        this.bedrockRuntimeClient = bedrockRuntimeClient;
    }

    @Override
    public ChatCompletionResult converse(ResolvedChatRequest request) {
        try {
            ConverseRequest.Builder requestBuilder = ConverseRequest.builder()
                    .modelId(request.modelId())
                    .system(buildSystemPrompt(request.systemPrompt()))
                    .messages(buildMessages(request))
                    .inferenceConfig(InferenceConfiguration.builder()
                            .maxTokens(request.maxTokens())
                            .temperature((float) request.temperature())
                            .topP((float) request.topP())
                            .build());

            String guardrailId = appConfig.guardrailId();
            String guardrailVersion = appConfig.guardrailVersion();
            boolean hasGuardrailId = guardrailId != null && !guardrailId.isBlank();
            boolean hasGuardrailVersion = guardrailVersion != null && !guardrailVersion.isBlank();

            if (!hasGuardrailId || !hasGuardrailVersion) {
                throw new IllegalArgumentException(
                        "BEDROCK_GUARDRAIL_ID/BEDROCK_GUARDRAIL_VERSION must be set when configured.");
            }

            requestBuilder.guardrailConfig(GuardrailConfiguration.builder()
                        .guardrailIdentifier(guardrailId.trim())
                        .guardrailVersion(guardrailVersion.trim())
                            .trace(GuardrailTrace.ENABLED)
                        .build());

            ConverseRequest converseRequest = requestBuilder.build();
            ConverseResponse response = bedrockRuntimeClient.converse(converseRequest);
            String reply = extractReply(response);
            TokenUsage usage = response.usage();

            return new ChatCompletionResult(
                    reply,
                    request.modelId(),
                    response.stopReasonAsString(),
                    usage == null || usage.inputTokens() == null ? 0 : usage.inputTokens(),
                    usage == null || usage.outputTokens() == null ? 0 : usage.outputTokens());
        } catch (Exception ex) {
            throw new BedrockChatException("Failed to invoke the Bedrock Converse API: "+ex.getMessage(), ex);
        }
    }

    private static List<SystemContentBlock> buildSystemPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return List.of();
        }

        return List.of(SystemContentBlock.builder()
                .text(systemPrompt)
                .build());
    }

    private static List<Message> buildMessages(ResolvedChatRequest request) {
        List<Message> messages = new ArrayList<>();

        for (ConversationTurn turn : request.conversation()) {
            messages.add(Message.builder()
                    .role(toConversationRole(turn.role()))
                    .content(ContentBlock.builder().text(turn.message()).build())
                    .build());
        }

        messages.add(Message.builder()
                .role(ConversationRole.USER)
                .content(ContentBlock.builder().text(request.message()).build())
                .build());

        return messages;
    }

    private static ConversationRole toConversationRole(String role) {
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "assistant" -> ConversationRole.ASSISTANT;
            case "user" -> ConversationRole.USER;
            default -> throw new IllegalArgumentException("Unsupported conversation role: " + role);
        };
    }

    private static String extractReply(ConverseResponse response) {
        if (response.output() == null || response.output().message() == null) {
            throw new BedrockChatException("Bedrock response did not contain an assistant message.");
        }

        String reply = response.output().message().content().stream()
                .map(ContentBlock::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"))
                .trim();

        if (reply.isEmpty()) {
            throw new BedrockChatException("Bedrock response contained no text content.");
        }

        return reply;
    }
}

