# Bedrock Chat

Production-oriented example project that exposes a `POST /chat` API on API Gateway, invokes an AWS Lambda written in Java 21, and calls the AWS Bedrock Converse API by using the AWS SDK for Java v2.

## What is included

- Java 21 Lambda handler using `aws-lambda-java-core`
- AWS SDK v2 `BedrockRuntimeClient` with the `converse` operation
- **Bedrock Guardrails** for content safety (PII detection and blocked topics)
- Input validation and structured JSON responses
- Gradle project that builds a single deployable Lambda JAR
- Terraform for IAM, Lambda, CloudWatch logs, API Gateway, API key, usage plan, and permissions
- Unit tests for request handling and request resolution

## Request flow

`Client -> API Gateway -> Lambda -> Bedrock Converse API -> Model`

(With optional Bedrock Guardrails applied to user input and model output for content filtering)

## Bedrock Guardrails

This project includes **AWS Bedrock Guardrails** for content safety. The guardrail is configured to:

- **Detect PII**: Email addresses, phone numbers, AWS credentials, and USA SSNs
- **Block harmful topics**: Self-harm, illegal activity, and adult content

When a guardrail violation is detected:
- The user receives: `"Your request contains restricted content and cannot be processed."`
- Model output violations return: `"I cannot provide that response due to content policy."`

### Enable/Disable Guardrails

To toggle guardrails during deployment, set the Terraform variable:

```terraform
enable_bedrock_guardrail = true  # or false to disable
bedrock_guardrail_version = "DRAFT" # or a pinned published version like "1"
```

### Demo: Testing Guardrails

Try these prompts to see the guardrail in action:

**1. Normal query (allowed):**
```json
{
  "message": "What are the benefits of cloud computing?"
}
```
✅ Returns a normal response.

**2. PII detection (blocked):**
```json
{
  "message": "My phone number is 555-123-4567 and my SSN is 123-45-6789. Store this securely."
}
```
❌ Guardrail detects and blocks PII; returns safety message.

**3. Illegal activity request (blocked):**
```json
{
  "message": "How do I hack into a bank's security system?"
}
```
❌ Guardrail detects illegal activity topic; returns safety message.

## Project layout

```text
bedrock-chat/
├── build.gradle
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
├── README.md
├── settings.gradle
├── src/
│   ├── main/java/com/example/bedrockchat/
│   │   ├── config/AppConfig.java
│   │   ├── handler/BedrockChatHandler.java
│   │   ├── model/ChatRequest.java
│   │   ├── model/ChatResponse.java
│   │   ├── model/ConversationTurn.java
│   │   └── service/
│   │       ├── AwsBedrockClient.java
│   │       ├── BedrockChatClient.java
│   │       ├── BedrockChatException.java
│   │       └── BedrockChatService.java
│   └── test/java/com/example/bedrockchat/
│       ├── handler/BedrockChatHandlerTest.java
│       └── service/BedrockChatServiceTest.java
└── terraform/
    ├── iam.tf
    ├── main.tf
    ├── outputs.tf
    ├── terraform.tfvars.example
    ├── variables.tf
    └── versions.tf
```

## Input contract

`POST /chat`

```json
{
  "message": "Give me a one sentence summary of Amazon Bedrock.",
  "systemPrompt": "You are a concise enterprise assistant.",
  "modelId": "amazon.nova-lite-v1:0",
  "maxTokens": 300,
  "temperature": 0.2,
  "topP": 0.9,
  "conversation": [
    { "role": "user", "message": "What is Bedrock?" },
    { "role": "assistant", "message": "Bedrock is a managed foundation model service." }
  ]
}
```

## Response contract

```json
{
  "requestId": "8af88f96-42eb-4ca7-98b6-df9034f07d07",
  "reply": "Amazon Bedrock is a managed AWS service for invoking and orchestrating foundation models through an API.",
  "modelId": "amazon.nova-lite-v1:0",
  "stopReason": "end_turn",
  "inputTokens": 128,
  "outputTokens": 32
}
```

## Build and test

From the project root:

```powershell
.\gradlew.bat test
.\gradlew.bat jar
```

The Lambda artifact is written to:

```text
build/libs/bedrock-chat.jar
```

## Deploy with Terraform

1. Build the JAR.
2. Copy `terraform/terraform.tfvars.example` to `terraform/terraform.tfvars` and adjust values if needed.
3. Apply Terraform.

```powershell
.\gradlew.bat jar
Set-Location .\terraform
terraform init
terraform plan
terraform apply
```

## Get the API key value

Terraform creates the API key resource. To retrieve the actual key value after apply, use the output `api_key_id` with the AWS CLI:

```powershell
aws apigateway get-api-key --api-key <api_key_id> --include-value --query value --output text
```

## Example invocation

```powershell
curl -X POST "https://<api-id>.execute-api.<region>.amazonaws.com/prod/chat" ^
  -H "x-api-key: <api-key>" ^
  -H "Content-Type: application/json" ^
  -d "{\"message\":\"Hello from Bedrock\"}"
```

If you are running from PowerShell directly, `Invoke-RestMethod` is usually easier:

```powershell
$body = @{
  message = 'Hello from Bedrock'
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri "https://<api-id>.execute-api.<region>.amazonaws.com/prod/chat" `
  -Headers @{ 'x-api-key' = '<api-key>' } `
  -ContentType 'application/json' `
  -Body $body
```

## Clean up

To remove the deployed AWS resources and stop future infrastructure charges:

```powershell
Set-Location .\terraform
terraform destroy
```

## Operational notes

- API Gateway REST APIs should be treated as synchronous request/response endpoints with an integration ceiling around 29 seconds.
- The Lambda timeout in this sample is set to 29 seconds to align with API Gateway.
- The Lambda execution role grants `bedrock:InvokeModel` and `bedrock:Converse` to the configured foundation model ARN, plus `bedrock:ApplyGuardrail` for guardrail evaluation.
- For larger deployment artifacts, switch the Terraform Lambda resource from `filename` to an S3-based deployment package.
- **Guardrails** are enabled by default (`enable_bedrock_guardrail = true` in Terraform). The Bedrock Guardrail resource manages allowed/denied topics and PII filtering. See "Bedrock Guardrails" section above for testing and configuration.
- When guardrails are disabled, the Lambda environment variables `BEDROCK_GUARDRAIL_ID` and `BEDROCK_GUARDRAIL_VERSION` will be empty strings; the SDK ignores them gracefully.

