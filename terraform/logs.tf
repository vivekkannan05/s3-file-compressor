# CloudWatch Log Group with retention policy (§IR-1.4)

resource "aws_cloudwatch_log_group" "compressor" {
  name              = "/aws/lambda/${aws_lambda_function.compressor.function_name}"
  retention_in_days = var.log_retention_days
}
