package com.example.bedrockchat.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
        String requestId,
        String reply,
        String modelId,
        String stopReason,
        Integer inputTokens,
        Integer outputTokens,
        String error) {

    public static ChatResponse success(
            String requestId,
            String reply,
            String modelId,
            String stopReason,
            Integer inputTokens,
            Integer outputTokens) {
        return new ChatResponse(requestId, reply, modelId, stopReason, inputTokens, outputTokens, null);
    }

    public static ChatResponse error(String requestId, String error) {
        return new ChatResponse(requestId, null, null, null, null, null, error);
    }
}

