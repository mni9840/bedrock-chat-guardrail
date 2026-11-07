package com.example.bedrockchat.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ConversationTurn(
        @JsonProperty("role") String role,
        @JsonProperty("message") String message) {
}

