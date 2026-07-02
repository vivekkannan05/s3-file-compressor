# Lambda function and IAM role (§IR-1.1, §IR-1.3)

data "aws_caller_identity" "current" {}

resource "aws_iam_role" "lambda_exec" {
  name = "s3-file-compressor-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_role_policy" "lambda_permissions" {
  name = "s3-file-compressor-permissions"
  role = aws_iam_role.lambda_exec.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "S3ReadWrite"
        Effect = "Allow"
        Action = [
          "s3:ListBucket",
          "s3:GetObject",
          "s3:PutObject"
        ]
        Resource = [
          aws_s3_bucket.compressor.arn,
          "${aws_s3_bucket.compressor.arn}/*"
        ]
      },
      {
        Sid    = "CloudWatchLogs"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = "arn:aws:logs:${var.aws_region}:${data.aws_caller_identity.current.account_id}:log-group:/aws/lambda/s3-file-compressor:*"
      },
      {
        Sid    = "DLQWrite"
        Effect = "Allow"
        Action = "sqs:SendMessage"
        Resource = aws_sqs_queue.dlq.arn
      }
    ]
  })
}

resource "aws_lambda_function" "compressor" {
  function_name = "s3-file-compressor"
  role          = aws_iam_role.lambda_exec.arn
  handler       = "not.used.in.native"
  runtime       = "provided.al2023"
  architectures = ["arm64"]
  memory_size   = var.lambda_memory_mb
  timeout       = var.lambda_timeout_sec

  reserved_concurrent_executions = 1

  filename         = "${path.module}/../target/function.zip"
  source_code_hash = filebase64sha256("${path.module}/../target/function.zip")

  dead_letter_config {
    target_arn = aws_sqs_queue.dlq.arn
  }

  environment {
    variables = {
      COMPRESSOR_BUCKET       = var.bucket_name
      COMPRESSOR_INPUT_PREFIX  = var.input_prefix
      COMPRESSOR_OUTPUT_PREFIX = var.output_prefix
      COMPRESSOR_ARCHIVE_NAME  = var.archive_name
    }
  }
}
