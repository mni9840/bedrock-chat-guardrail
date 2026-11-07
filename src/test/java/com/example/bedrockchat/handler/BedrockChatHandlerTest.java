package com.example.bedrockchat.handler;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.example.bedrockchat.config.AppConfig;
import com.example.bedrockchat.model.ChatRequest;
import com.example.bedrockchat.service.ChatCompletionResult;
import com.example.bedrockchat.service.BedrockChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BedrockChatHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void returnsSuccessForValidRequest() throws Exception {
        BedrockChatService service = new BedrockChatService(
                new AppConfig(Region.US_EAST_1, "amazon.nova-lite-v1:0", 512, 0.2d, 0.9d, "", ""),
                request -> new ChatCompletionResult(
                        "Hello from Bedrock",
                        request.modelId(),
                        "end_turn",
                        12,
                        4));
        BedrockChatHandler handler = new BedrockChatHandler(service, objectMapper);

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody(objectMapper.writeValueAsString(new ChatRequest("Hi", null, null, null, null, null, List.of())));

        var response = handler.handleRequest(event, new TestContext());

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Hello from Bedrock");
        assertThat(response.getHeaders()).containsEntry("Content-Type", "application/json");
    }

    @Test
    void returnsBadRequestForInvalidJson() {
        BedrockChatService service = new BedrockChatService(
                new AppConfig(Region.US_EAST_1, "amazon.nova-lite-v1:0", 512, 0.2d, 0.9d, "", ""),
                request -> new ChatCompletionResult("ignored", request.modelId(), "end_turn", 0, 0));
        BedrockChatHandler handler = new BedrockChatHandler(service, objectMapper);

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withBody("not-json");

        var response = handler.handleRequest(event, new TestContext());

        assertThat(response.getStatusCode()).isEqualTo(400);
        assertThat(response.getBody()).contains("valid JSON");
    }

    @Test
    void decodesBase64EncodedBodies() throws Exception {
        BedrockChatService service = new BedrockChatService(
                new AppConfig(Region.US_EAST_1, "amazon.nova-lite-v1:0", 512, 0.2d, 0.9d, "", ""),
                request -> new ChatCompletionResult(
                        "Decoded",
                        request.modelId(),
                        "end_turn",
                        1,
                        1));
        BedrockChatHandler handler = new BedrockChatHandler(service, objectMapper);

        String json = objectMapper.writeValueAsString(new ChatRequest("Hi", null, null, null, null, null, List.of()));
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withIsBase64Encoded(true)
                .withBody(Base64.getEncoder().encodeToString(json.getBytes()));

        var response = handler.handleRequest(event, new TestContext());

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getBody()).contains("Decoded");
    }

    private static final class TestContext implements Context {

        @Override
        public String getAwsRequestId() {
            return "test-request-id";
        }

        @Override
        public String getLogGroupName() {
            return "log-group";
        }

        @Override
        public String getLogStreamName() {
            return "log-stream";
        }

        @Override
        public String getFunctionName() {
            return "bedrock-chat-guardrail";
        }

        @Override
        public String getFunctionVersion() {
            return "$LATEST";
        }

        @Override
        public String getInvokedFunctionArn() {
            return "arn:aws:lambda:us-east-1:123456789012:function:bedrock-chat";
        }

        @Override
        public CognitoIdentity getIdentity() {
            return null;
        }

        @Override
        public ClientContext getClientContext() {
            return null;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return 10_000;
        }

        @Override
        public int getMemoryLimitInMB() {
            return 512;
        }

        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override
                public void log(String message) {
                }

                @Override
                public void log(byte[] message) {
                }
            };
        }
    }
}

