output "lambda_function_arn" {
  description = "ARN of the deployed Lambda function"
  value       = aws_lambda_function.compressor.arn
}

output "s3_bucket_name" {
  description = "Name of the S3 bucket"
  value       = aws_s3_bucket.compressor.id
}

output "eventbridge_schedule_arn" {
  description = "ARN of the EventBridge schedule"
  value       = aws_scheduler_schedule.daily_compression.arn
}

output "lambda_log_group" {
  description = "CloudWatch Log Group name"
  value       = aws_cloudwatch_log_group.compressor.name
}

output "alarm_sns_topic_arn" {
  description = "SNS topic ARN for alarm notifications"
  value       = aws_sns_topic.alarm_notifications.arn
}

output "dlq_url" {
  description = "SQS dead letter queue URL"
  value       = aws_sqs_queue.dlq.url
}
