# S3 bucket for input files and compressed outputs (§IR-1.6)

resource "aws_s3_bucket" "compressor" {
  bucket = var.bucket_name
}

resource "aws_s3_bucket_versioning" "compressor" {
  bucket = aws_s3_bucket.compressor.id

  versioning_configuration {
    status = "Disabled"
  }
}

resource "aws_s3_bucket_public_access_block" "compressor" {
  bucket = aws_s3_bucket.compressor.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# SSE-S3 encryption at rest (§NFR-5.3)
resource "aws_s3_bucket_server_side_encryption_configuration" "compressor" {
  bucket = aws_s3_bucket.compressor.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
    bucket_key_enabled = true
  }
}

# Enforce HTTPS in transit (§NFR-5.4)
resource "aws_s3_bucket_policy" "enforce_https" {
  bucket = aws_s3_bucket.compressor.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "EnforceHTTPS"
        Effect    = "Deny"
        Principal = "*"
        Action    = "s3:*"
        Resource = [
          aws_s3_bucket.compressor.arn,
          "${aws_s3_bucket.compressor.arn}/*"
        ]
        Condition = {
          Bool = {
            "aws:SecureTransport" = "false"
          }
        }
      }
    ]
  })
}

# Lifecycle policy to expire old outputs (§NFR-6.3)
resource "aws_s3_bucket_lifecycle_configuration" "output_expiration" {
  bucket = aws_s3_bucket.compressor.id

  rule {
    id     = "expire-old-outputs"
    status = "Enabled"

    filter {
      prefix = var.output_prefix
    }

    expiration {
      days = var.output_expiration_days
    }
  }
}
