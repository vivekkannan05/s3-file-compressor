# CloudWatch Alarm and SNS notification for Lambda errors (§IR-1.7, §NFR-4.3)

resource "aws_sns_topic" "alarm_notifications" {
  name = "s3-file-compressor-alarms"
}

resource "aws_cloudwatch_metric_alarm" "lambda_errors" {
  alarm_name          = "s3-file-compressor-errors"
  alarm_description   = "Fires when the S3 File Compressor Lambda has errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "Errors"
  namespace           = "AWS/Lambda"
  period              = 300
  statistic           = "Sum"
  threshold           = 0
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.compressor.function_name
  }

  alarm_actions = [aws_sns_topic.alarm_notifications.arn]
  ok_actions    = [aws_sns_topic.alarm_notifications.arn]
}

# SQS dead letter queue for failed invocations (§IR-1.8, §NFR-4.4)

resource "aws_sqs_queue" "dlq" {
  name                      = "s3-file-compressor-dlq"
  message_retention_seconds = 1209600 # 14 days
}
