package com.example.bedrockchat.config;

import software.amazon.awssdk.regions.Region;

public record AppConfig(
        Region region,
        String defaultModelId,
        int defaultMaxTokens,
        double defaultTemperature,
        double defaultTopP,
        String guardrailId,
        String guardrailVersion) {

    private static final String DEFAULT_REGION = "us-east-1";
    private static final String DEFAULT_MODEL_ID = "amazon.nova-lite-v1:0";
    private static final int DEFAULT_MAX_TOKENS = 512;
    private static final double DEFAULT_TEMPERATURE = 0.2d;
    private static final double DEFAULT_TOP_P = 0.9d;

    public static AppConfig fromEnvironment() {
        return new AppConfig(
                Region.of(readString("AWS_REGION", DEFAULT_REGION)),
                readString("BEDROCK_MODEL_ID", DEFAULT_MODEL_ID),
                readInt("BEDROCK_MAX_TOKENS", DEFAULT_MAX_TOKENS),
                readDouble("BEDROCK_TEMPERATURE", DEFAULT_TEMPERATURE),
                readDouble("BEDROCK_TOP_P", DEFAULT_TOP_P),
                readOptionalString("BEDROCK_GUARDRAIL_ID"),
                readOptionalString("BEDROCK_GUARDRAIL_VERSION"));
    }

    private static String readOptionalString(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String readString(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static int readInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private static double readDouble(String name, double defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}

