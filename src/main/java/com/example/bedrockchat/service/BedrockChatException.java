package com.example.bedrockchat.service;

public class BedrockChatException extends RuntimeException {

    public BedrockChatException(String message, Throwable cause) {
        super(message, cause);
    }

    public BedrockChatException(String message) {
        super(message);
    }
}

