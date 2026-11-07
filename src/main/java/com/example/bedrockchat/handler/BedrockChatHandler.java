package com.example.bedrockchat.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.example.bedrockchat.config.AppConfig;
import com.example.bedrockchat.model.ChatRequest;
import com.example.bedrockchat.model.ChatResponse;
import com.example.bedrockchat.service.AwsBedrockClient;
import com.example.bedrockchat.service.BedrockChatException;
import com.example.bedrockchat.service.BedrockChatService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class BedrockChatHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper;
    private final BedrockChatService chatService;

    public BedrockChatHandler() {
        this(defaultService(), defaultObjectMapper());
    }

    BedrockChatHandler(BedrockChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String requestId = context == null ? null : context.getAwsRequestId();

        try {
            if (event == null) {
                return jsonResponse(400, ChatResponse.error(requestId, "Request event is required."));
            }
            if (event.getHttpMethod() != null && !"POST".equalsIgnoreCase(event.getHttpMethod())) {
                return jsonResponse(405, ChatResponse.error(requestId, "Only POST is supported."));
            }

            ChatRequest request = objectMapper.readValue(readBody(event), ChatRequest.class);
            assert context != null;
            context.getLogger().log("AppConfig: "+chatService.getAppConfig());
            context.getLogger().log("ChatRequest: " +request);
            ChatResponse response = chatService.chat(request, requestId, context.getLogger());
            context.getLogger().log("ChatResponse: " +response);
            return jsonResponse(200, response);
        } catch (JsonProcessingException ex) {
            return jsonResponse(400, ChatResponse.error(requestId, "Request body must be valid JSON."));
        } catch (IllegalArgumentException ex) {
            return jsonResponse(400, ChatResponse.error(requestId, ex.getMessage()));
        } catch (BedrockChatException ex) {
            log(context, ex);
            return jsonResponse(502, ChatResponse.error(requestId, "Upstream Bedrock invocation failed."));
        } catch (Exception ex) {
            log(context, ex);
            return jsonResponse(500, ChatResponse.error(requestId, "Internal server error."));
        }
    }

    private String readBody(APIGatewayProxyRequestEvent event) {
        String body = event.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (Boolean.TRUE.equals(event.getIsBase64Encoded())) {
            return new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
        }
        return body;
    }

    private APIGatewayProxyResponseEvent jsonResponse(int statusCode, ChatResponse payload) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode)
                    .withHeaders(defaultHeaders())
                    .withBody(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize Lambda response.", ex);
        }
    }

    private static Map<String, String> defaultHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Cache-Control", "no-store");
        headers.put("Access-Control-Allow-Origin", "*");
        headers.put("Access-Control-Allow-Headers", "Content-Type,x-api-key");
        headers.put("Access-Control-Allow-Methods", "POST,OPTIONS");
        return headers;
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private static BedrockChatService defaultService() {
        AppConfig appConfig = AppConfig.fromEnvironment();
        // Cold-start log to verify Terraform-provided runtime settings quickly in CloudWatch.
        return new BedrockChatService(appConfig, new AwsBedrockClient(appConfig));
    }

    private static String guardrailStatus(AppConfig appConfig) {
        if (appConfig.guardrailId() == null || appConfig.guardrailId().isBlank()) {
            return "guardrail=disabled";
        }

        String guardrailId = appConfig.guardrailId();
        String maskedId = guardrailId.length() <= 8
                ? guardrailId
                : guardrailId.substring(0, 4) + "..." + guardrailId.substring(guardrailId.length() - 4);
        String version = appConfig.guardrailVersion() == null || appConfig.guardrailVersion().isBlank()
                ? "<missing>"
                : appConfig.guardrailVersion();
        return "guardrail=enabled(id=" + maskedId + ",version=" + version + ")";
    }

    private static void log(Context context, Exception ex) {
        if (context != null && context.getLogger() != null) {
            context.getLogger().log(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}

