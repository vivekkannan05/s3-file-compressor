# EventBridge Scheduler for daily Lambda trigger (§IR-1.2)

resource "aws_iam_role" "scheduler_exec" {
  name = "s3-file-compressor-scheduler-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "scheduler.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "scheduler_invoke_lambda" {
  name = "invoke-compressor-lambda"
  role = aws_iam_role.scheduler_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "lambda:InvokeFunction"
        Resource = aws_lambda_function.compressor.arn
      }
    ]
  })
}

resource "aws_scheduler_schedule" "daily_compression" {
  name       = "s3-file-compressor-daily"
  group_name = "default"

  flexible_time_window {
    mode = "OFF"
  }

  schedule_expression          = var.schedule_expression
  schedule_expression_timezone = "UTC"

  target {
    arn      = aws_lambda_function.compressor.arn
    role_arn = aws_iam_role.scheduler_exec.arn

    input = jsonencode({})
  }
}
