variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Base name used for provisioned resources."
  type        = string
  default     = "bedrock-chat-guardrail"
}

variable "environment" {
  description = "Environment name used for stage names and tags."
  type        = string
  default     = "prod"
}

variable "bedrock_model_id" {
  description = "Foundation model ID to invoke through Bedrock."
  type        = string
  default     = "amazon.nova-lite-v1:0"
}

variable "lambda_jar_path" {
  description = "Path to the Lambda JAR, relative to the terraform directory."
  type        = string
  default     = "../build/libs/bedrock-chat-lambda.jar"
}

variable "lambda_memory_mb" {
  description = "Configured Lambda memory size in MB."
  type        = number
  default     = 1024
}

variable "lambda_timeout_seconds" {
  description = "Lambda timeout in seconds. Keep this at or below the synchronous API Gateway ceiling."
  type        = number
  default     = 25
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days."
  type        = number
  default     = 3
}

variable "enable_bedrock_guardrail" {
  description = "Enable Bedrock guardrail for content safety (PII detection and blocked topics)."
  type        = bool
  default     = true
}

variable "bedrock_guardrail_version" {
  description = "Bedrock guardrail version used during runtime checks (for example: DRAFT or numeric version like 1)."
  type        = string
  default     = "DRAFT"
}

