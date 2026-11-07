output "api_invoke_url" {
  description = "Base invoke URL for the deployed API stage."
  value       = aws_api_gateway_stage.this.invoke_url
}

output "chat_endpoint" {
  description = "Full URL for the POST /chat endpoint."
  value       = "${aws_api_gateway_stage.this.invoke_url}/chat"
}

output "api_key_id" {
  description = "API key identifier. Use the AWS CLI to fetch the key value."
  value       = aws_api_gateway_api_key.this.id
}

output "lambda_function_name" {
  description = "Name of the Lambda function deployed by Terraform."
  value       = aws_lambda_function.bedrock_chat.function_name
}

output "lambda_role_arn" {
  description = "ARN of the Lambda execution role."
  value       = aws_iam_role.lambda_execution.arn
}

output "guardrail_id" {
  description = "Bedrock Guardrail ID (empty string if guardrails are disabled)."
  value       = var.enable_bedrock_guardrail ? aws_bedrock_guardrail.chat_guardrail[0].guardrail_id : ""
}

output "guardrail_name" {
  description = "Bedrock Guardrail name (empty string if guardrails are disabled)."
  value       = var.enable_bedrock_guardrail ? aws_bedrock_guardrail.chat_guardrail[0].name : ""
}

output "guardrail_version" {
  description = "Bedrock Guardrail version configured for runtime requests (empty string if guardrails are disabled)."
  value       = var.enable_bedrock_guardrail ? var.bedrock_guardrail_version : ""
}

