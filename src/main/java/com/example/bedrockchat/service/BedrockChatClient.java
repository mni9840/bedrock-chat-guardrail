package com.example.bedrockchat.service;

public interface BedrockChatClient {

    ChatCompletionResult converse(ResolvedChatRequest request);
}

