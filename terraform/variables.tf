variable "aws_region" {
  description = "AWS region to deploy into"
  type        = string
  default     = "us-east-1"
}

variable "bucket_name" {
  description = "S3 bucket name for input and output files"
  type        = string
}

variable "input_prefix" {
  description = "S3 prefix where source files are stored"
  type        = string
  default     = "input/"
}

variable "output_prefix" {
  description = "S3 prefix where compressed outputs are written"
  type        = string
  default     = "output/"
}

variable "archive_name" {
  description = "Base name for .zip and .tar outputs"
  type        = string
  default     = "archive"
}

variable "schedule_expression" {
  description = "EventBridge cron expression for daily trigger (§IR-1.2)"
  type        = string
  default     = "cron(0 2 * * ? *)"
}

variable "lambda_memory_mb" {
  description = "Lambda memory allocation in MB (§IR-1.3)"
  type        = number
  default     = 512
}

variable "lambda_timeout_sec" {
  description = "Lambda timeout in seconds (§IR-1.3)"
  type        = number
  default     = 120
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days (§IR-1.4)"
  type        = number
  default     = 30
}

variable "output_expiration_days" {
  description = "Days before compressed outputs expire in S3 (§NFR-6.3)"
  type        = number
  default     = 90
}
