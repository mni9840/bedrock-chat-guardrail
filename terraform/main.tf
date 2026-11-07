data "aws_caller_identity" "current" {}

locals {
  name_prefix = "${var.project_name}-${var.environment}"
  lambda_name = "${local.name_prefix}-lambda"
  api_name    = "${local.name_prefix}-api"

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${local.lambda_name}"
  retention_in_days = var.log_retention_days
  tags              = local.common_tags
}

resource "aws_cloudwatch_log_group" "api_gateway_access" {
  name              = "/aws/apigateway/${local.api_name}-access"
  retention_in_days = var.log_retention_days
  tags              = local.common_tags
}

resource "aws_lambda_function" "bedrock_chat" {
  function_name = local.lambda_name
  description   = "Lambda front-end for AWS Bedrock Converse API"
  role          = aws_iam_role.lambda_execution.arn
  runtime       = "java21"
  handler       = "com.example.bedrockchat.handler.BedrockChatHandler::handleRequest"
  filename      = var.lambda_jar_path

  source_code_hash = filebase64sha256(var.lambda_jar_path)
  memory_size      = var.lambda_memory_mb
  timeout          = var.lambda_timeout_seconds
  publish          = true

  environment {
    variables = {
      BEDROCK_MODEL_ID          = var.bedrock_model_id
      BEDROCK_GUARDRAIL_ID      = var.enable_bedrock_guardrail ? aws_bedrock_guardrail.chat_guardrail[0].guardrail_id : ""
      BEDROCK_GUARDRAIL_VERSION = var.enable_bedrock_guardrail ? var.bedrock_guardrail_version : ""
      BEDROCK_MAX_TOKENS        = "512"
      BEDROCK_TEMPERATURE       = "0.2"
      BEDROCK_TOP_P             = "0.9"
    }
  }

  depends_on = [
    aws_cloudwatch_log_group.lambda,
    aws_iam_role_policy_attachment.lambda_basic_execution,
    aws_iam_role_policy.lambda_bedrock_access
  ]

  tags = local.common_tags
}

resource "aws_api_gateway_rest_api" "this" {
  name        = local.api_name
  description = "API Gateway front door for the Bedrock chat Lambda"

  endpoint_configuration {
    types = ["REGIONAL"]
  }

  tags = local.common_tags
}

resource "aws_api_gateway_resource" "chat" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_rest_api.this.root_resource_id
  path_part   = "chat"
}

resource "aws_api_gateway_method" "post_chat" {
  rest_api_id      = aws_api_gateway_rest_api.this.id
  resource_id      = aws_api_gateway_resource.chat.id
  http_method      = "POST"
  authorization    = "NONE"
  api_key_required = true
}

resource "aws_api_gateway_integration" "post_chat" {
  rest_api_id             = aws_api_gateway_rest_api.this.id
  resource_id             = aws_api_gateway_resource.chat.id
  http_method             = aws_api_gateway_method.post_chat.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.bedrock_chat.invoke_arn
}

resource "aws_api_gateway_method" "options_chat" {
  rest_api_id   = aws_api_gateway_rest_api.this.id
  resource_id   = aws_api_gateway_resource.chat.id
  http_method   = "OPTIONS"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "options_chat" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  resource_id = aws_api_gateway_resource.chat.id
  http_method = aws_api_gateway_method.options_chat.http_method
  type        = "MOCK"

  request_templates = {
    "application/json" = "{\"statusCode\": 200}"
  }
}

resource "aws_api_gateway_method_response" "options_chat" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  resource_id = aws_api_gateway_resource.chat.id
  http_method = aws_api_gateway_method.options_chat.http_method
  status_code = "200"

  response_models = {
    "application/json" = "Empty"
  }

  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = true
    "method.response.header.Access-Control-Allow-Methods" = true
    "method.response.header.Access-Control-Allow-Origin"  = true
  }
}

resource "aws_api_gateway_integration_response" "options_chat" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  resource_id = aws_api_gateway_resource.chat.id
  http_method = aws_api_gateway_method.options_chat.http_method
  status_code = aws_api_gateway_method_response.options_chat.status_code

  response_parameters = {
    "method.response.header.Access-Control-Allow-Headers" = "'Content-Type,x-api-key'"
    "method.response.header.Access-Control-Allow-Methods" = "'POST,OPTIONS'"
    "method.response.header.Access-Control-Allow-Origin"  = "'*'"
  }
}

resource "aws_lambda_permission" "allow_api_gateway" {
  statement_id  = "AllowExecutionFromApiGateway"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.bedrock_chat.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_api_gateway_rest_api.this.execution_arn}/*/*"
}

resource "aws_api_gateway_deployment" "this" {
  rest_api_id = aws_api_gateway_rest_api.this.id

  triggers = {
    redeployment = sha1(jsonencode({
      post_method         = aws_api_gateway_method.post_chat.id
      post_integration    = aws_api_gateway_integration.post_chat.id
      options_method      = aws_api_gateway_method.options_chat.id
      options_integration = aws_api_gateway_integration.options_chat.id
    }))
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_api_gateway_stage" "this" {
  rest_api_id   = aws_api_gateway_rest_api.this.id
  deployment_id = aws_api_gateway_deployment.this.id
  stage_name    = var.environment

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_gateway_access.arn
    format = jsonencode({
      requestId      = "$context.requestId"
      ip             = "$context.identity.sourceIp"
      caller         = "$context.identity.caller"
      user           = "$context.identity.user"
      requestTime    = "$context.requestTime"
      httpMethod     = "$context.httpMethod"
      resourcePath   = "$context.resourcePath"
      status         = "$context.status"
      protocol       = "$context.protocol"
      responseLength = "$context.responseLength"
    })
  }

  depends_on = [aws_api_gateway_account.this]

  tags = local.common_tags
}

resource "aws_api_gateway_method_settings" "all" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  stage_name  = aws_api_gateway_stage.this.stage_name
  method_path = "*/*"

  settings {
    metrics_enabled    = true
    logging_level      = "INFO"
    data_trace_enabled = false
  }
}

resource "aws_api_gateway_api_key" "this" {
  name    = "${local.name_prefix}-api-key"
  enabled = true
  tags    = local.common_tags
}

resource "aws_api_gateway_usage_plan" "this" {
  name        = "${local.name_prefix}-usage-plan"
  description = "Usage plan for the Bedrock chat API"

  api_stages {
    api_id = aws_api_gateway_rest_api.this.id
    stage  = aws_api_gateway_stage.this.stage_name
  }

  throttle_settings {
    burst_limit = 100
    rate_limit  = 50
  }

  quota_settings {
    limit  = 10000
    period = "MONTH"
  }

  tags = local.common_tags
}

resource "aws_api_gateway_usage_plan_key" "this" {
  key_id        = aws_api_gateway_api_key.this.id
  key_type      = "API_KEY"
  usage_plan_id = aws_api_gateway_usage_plan.this.id
}

resource "aws_bedrock_guardrail" "chat_guardrail" {
  count       = var.enable_bedrock_guardrail ? 1 : 0
  name        = "${local.name_prefix}-guardrail"
  description = "Guardrail for chat API: PII detection and blocked topics"

  blocked_input_messaging   = "Your request contains restricted content and cannot be processed."
  blocked_outputs_messaging = "I cannot provide that response due to content policy."

  content_policy_config {
    filters_config {
      type            = "SEXUAL"
      input_strength  = "MEDIUM"
      output_strength = "MEDIUM"
    }

    filters_config {
      type            = "VIOLENCE"
      input_strength  = "MEDIUM"
      output_strength = "MEDIUM"
    }
  }

  topic_policy_config {
    topics_config {
      name       = "self_harm"
      definition = "Self-harm, suicide, or instructions for causing harm to oneself"
      examples   = ["how to harm yourself", "suicide methods", "self injury instructions"]
      type       = "DENY"
    }

    topics_config {
      name       = "illegal_activity"
      definition = "Instructions, encouragement, or guidance for illegal activities"
      examples   = ["how to hack a system", "drug synthesis", "credit card fraud"]
      type       = "DENY"
    }

    topics_config {
      name       = "adult_content"
      definition = "Explicit adult, sexual, or NSFW content"
      examples   = ["pornographic material", "explicit sexual content"]
      type       = "DENY"
    }
  }

  sensitive_information_policy_config {
    pii_entities_config {
      action = "BLOCK"
      type   = "EMAIL"
    }

    pii_entities_config {
      action = "BLOCK"
      type   = "PHONE"
    }

    pii_entities_config {
      action = "BLOCK"
      type   = "US_SOCIAL_SECURITY_NUMBER"
    }
  }

  tags = local.common_tags
}
