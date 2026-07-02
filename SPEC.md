# S3 File Compressor Lambda — Specification

**Version:** 2.0
**Date:** 2026-07-01
**Status:** Draft (Well-Architected Review Applied)

---

## 1. Overview

A Quarkus-based AWS Lambda function that reads files from an S3 prefix,
compresses them into three output formats (.zip, .tar, .gz), and writes
the results back to the same S3 bucket under a different prefix.

The Lambda is triggered daily by an EventBridge Scheduler rule.

---

## 2. Functional Requirements

### FR-1: File Discovery

| ID    | Requirement |
|-------|-------------|
| FR-1.1 | The system SHALL list all objects under a configurable S3 prefix (e.g., `input/`). |
| FR-1.2 | The system SHALL operate against a single, configurable S3 bucket. |
| FR-1.3 | The system SHALL NOT recurse into sub-prefixes (flat listing only). |
| FR-1.4 | If the prefix contains zero files, the system SHALL complete successfully with an empty summary and produce no output files. |
| FR-1.5 | The system SHALL paginate S3 listing requests to handle prefixes with more than 1000 objects. |

### FR-2: File Download

| ID    | Requirement |
|-------|-------------|
| FR-2.1 | The system SHALL download each discovered file into memory (byte array). |
| FR-2.2 | If a single file download fails, the system SHALL log the failure and continue processing remaining files. |
| FR-2.3 | The system SHALL preserve the original file name (without the prefix) for use inside archives. |
| FR-2.4 | The system SHALL download files in parallel using virtual threads. |
| FR-2.5 | The system SHALL validate file names and reject path traversal characters (`..`, leading `/`) to prevent Zip Slip attacks. |
| FR-2.6 | The system SHALL track cumulative downloaded bytes and fail early if a configurable memory threshold is exceeded (default: 100 MB). |

### FR-3: Compression — ZIP

| ID    | Requirement |
|-------|-------------|
| FR-3.1 | The system SHALL create a single `.zip` archive containing ALL successfully downloaded files. |
| FR-3.2 | Each entry in the zip SHALL use the original file name (not the full S3 key). |
| FR-3.3 | The output key SHALL be `{output-prefix}/{archive-name}.zip`. |

### FR-4: Compression — TAR

| ID    | Requirement |
|-------|-------------|
| FR-4.1 | The system SHALL create a single `.tar` archive containing ALL successfully downloaded files. |
| FR-4.2 | Each entry in the tar SHALL use the original file name. |
| FR-4.3 | The output key SHALL be `{output-prefix}/{archive-name}.tar`. |

### FR-5: Compression — GZIP

| ID    | Requirement |
|-------|-------------|
| FR-5.1 | The system SHALL create one `.gz` file PER successfully downloaded source file. |
| FR-5.2 | The output key for each SHALL be `{output-prefix}/{original-filename}.gz`. |
| FR-5.3 | Each `.gz` file SHALL contain the raw gzip-compressed bytes of the single source file. |

### FR-6: Output Upload

| ID    | Requirement |
|-------|-------------|
| FR-6.1 | The system SHALL upload all compressed outputs to `{output-prefix}/` in the same S3 bucket. |
| FR-6.2 | If an upload fails, the system SHALL log the error but NOT fail the entire run. |
| FR-6.3 | The system SHALL set the correct `Content-Type` on uploads: `application/zip`, `application/x-tar`, or `application/gzip`. |

### FR-7: Execution Summary

| ID    | Requirement |
|-------|-------------|
| FR-7.1 | The system SHALL log a JSON summary containing: total files found, files successfully processed, files skipped (with reasons), strategy failures, and output keys written. |
| FR-7.2 | The Lambda return value SHALL be this summary object. |
| FR-7.3 | Error messages in the response SHALL be sanitized (no AWS account IDs, ARNs, or internal endpoint URLs). Full details SHALL be logged internally only. |
| FR-7.4 | The system SHALL check `context.getRemainingTimeInMillis()` and abort gracefully before the Lambda timeout, avoiding partial/corrupt uploads. |

---

## 3. Non-Functional Requirements

### NFR-1: Performance

| ID     | Requirement |
|--------|-------------|
| NFR-1.1 | The system SHALL complete within 60 seconds for up to 50 MB of total input. |
| NFR-1.2 | The system SHALL use in-memory processing (no disk I/O to /tmp). |
| NFR-1.3 | The system SHALL pre-size `ByteArrayOutputStream` buffers based on known input sizes. |
| NFR-1.4 | The Lambda SHALL use `arm64` (Graviton) architecture for cost and performance optimization. |

### NFR-2: Runtime

| ID     | Requirement |
|--------|-------------|
| NFR-2.1 | The system SHALL be compiled as a GraalVM native image. |
| NFR-2.2 | Cold start time SHALL be under 500 ms. |
| NFR-2.3 | The system SHALL use Quarkus 3.33 LTS (supported until March 2027). |

### NFR-3: Configuration

| ID     | Requirement |
|--------|-------------|
| NFR-3.1 | All parameters SHALL be configurable via environment variables. |
| NFR-3.2 | Required config: `COMPRESSOR_BUCKET`, `COMPRESSOR_INPUT_PREFIX`, `COMPRESSOR_OUTPUT_PREFIX`. |
| NFR-3.3 | Optional config: `COMPRESSOR_ARCHIVE_NAME` (default: `archive`). |

### NFR-4: Observability

| ID     | Requirement |
|--------|-------------|
| NFR-4.1 | The system SHALL use structured (JSON) logging. |
| NFR-4.2 | Each file operation (download, compress, upload) SHALL produce a log entry with the file key and outcome. |
| NFR-4.3 | A CloudWatch Alarm SHALL fire when Lambda `Errors` metric > 0. |
| NFR-4.4 | Failed Lambda invocations SHALL be routed to an SQS dead letter queue. |

### NFR-5: Security

| ID     | Requirement |
|--------|-------------|
| NFR-5.1 | In production, the system SHALL use IAM execution role credentials via the default provider chain. No static credentials. |
| NFR-5.2 | Dev/test profiles SHALL use static throwaway credentials (`test`/`test`) for Floci emulation only. |
| NFR-5.3 | The S3 bucket SHALL enforce server-side encryption (SSE-S3, AES256). |
| NFR-5.4 | The S3 bucket SHALL enforce encryption in transit via a bucket policy requiring `aws:SecureTransport`. |
| NFR-5.5 | The Lambda SHALL have `reserved_concurrent_executions = 1` to prevent runaway scaling. |
| NFR-5.6 | Archive entry names SHALL be validated to prevent Zip Slip path traversal attacks. |

### NFR-6: Reliability

| ID     | Requirement |
|--------|-------------|
| NFR-6.1 | The system is idempotent by design: re-invocation overwrites outputs in `{output-prefix}/`. |
| NFR-6.2 | Compression strategy failures SHALL be tracked and reflected in the execution summary (not silently swallowed). |
| NFR-6.3 | The S3 `output/` prefix SHALL have a configurable lifecycle expiration policy (default: 90 days). |

---

## 4. Interface Definitions

### 4.1 Lambda Input (EventBridge Scheduled Event)

The Lambda receives an EventBridge scheduled event. The payload is ignored;
all configuration comes from environment variables.

```json
{
  "version": "0",
  "id": "...",
  "source": "aws.scheduler",
  "detail-type": "Scheduled Event",
  "detail": {}
}
```

### 4.2 Lambda Output (Execution Summary)

```json
{
  "status": "COMPLETED",
  "totalFilesFound": 3,
  "filesProcessed": 3,
  "filesSkipped": [],
  "outputsWritten": [
    "output/archive.zip",
    "output/archive.tar",
    "output/sample.txt.gz",
    "output/report.csv.gz",
    "output/config.json.gz"
  ],
  "durationMs": 1234
}
```

### 4.3 Partial Failure Output

```json
{
  "status": "COMPLETED_WITH_ERRORS",
  "totalFilesFound": 3,
  "filesProcessed": 2,
  "filesSkipped": [
    { "key": "input/corrupt.dat", "reason": "S3 download failed: Access Denied" }
  ],
  "outputsWritten": [
    "output/archive.zip",
    "output/archive.tar",
    "output/sample.txt.gz",
    "output/config.json.gz"
  ],
  "durationMs": 2345
}
```

---

## 5. Edge Cases

| ID   | Scenario | Expected Behavior |
|------|----------|-------------------|
| EC-1 | Input prefix is empty (no files) | Return success with 0 files processed, no outputs |
| EC-2 | All file downloads fail | Return `COMPLETED_WITH_ERRORS`, 0 processed, no outputs |
| EC-3 | Single file in prefix | Zip and tar contain 1 entry; 1 gz file produced |
| EC-4 | File with special characters in name | Preserve name as-is in archive entries |
| EC-5 | Very large single file (>10 MB) | Process normally (within memory limits) |
| EC-6 | Duplicate file names (shouldn't happen with flat prefix) | Overwrite in archive |

---

## 6. Technology Stack

| Component | Choice | Rationale |
|-----------|--------|-----------|
| Framework | Quarkus 3.33 LTS | Lambda-optimized, native image support, LTS until Mar 2027 |
| Language | Java 21 | LTS, modern features (records, sealed classes) |
| S3 Client | Quarkus Amazon S3 (AWS SDK v2) | Native integration |
| ZIP | `java.util.zip.ZipOutputStream` | JDK built-in, no dependency |
| GZIP | `java.util.zip.GZIPOutputStream` | JDK built-in, no dependency |
| TAR | Apache Commons Compress | JDK has no tar support |
| Build | Maven | Quarkus standard |
| Native | GraalVM Mandrel | Quarkus-recommended distribution |
| Local emulator | Floci (Docker) | Drop-in LocalStack replacement, MIT, no auth token |

---

## 7. Acceptance Criteria

These criteria are derived directly from the functional requirements above.
Each maps to one or more test cases.

### AC-1: Happy Path (from FR-1, FR-2, FR-3, FR-4, FR-5, FR-6, FR-7)

**Given** 3 files exist in `s3://bucket/input/` (sample.txt, report.csv, config.json)
**When** the Lambda is invoked
**Then**:
- `output/archive.zip` exists and contains 3 entries with correct content
- `output/archive.tar` exists and contains 3 entries with correct content
- `output/sample.txt.gz` exists and decompresses to original content
- `output/report.csv.gz` exists and decompresses to original content
- `output/config.json.gz` exists and decompresses to original content
- Return value has `status=COMPLETED`, `filesProcessed=3`, `filesSkipped=[]`

### AC-2: Empty Prefix (from FR-1.4, EC-1)

**Given** no files exist in `s3://bucket/input/`
**When** the Lambda is invoked
**Then**:
- No output files are created
- Return value has `status=COMPLETED`, `filesProcessed=0`

### AC-3: Partial Failure (from FR-2.2, EC-2)

**Given** 3 files exist but 1 is inaccessible
**When** the Lambda is invoked
**Then**:
- Archives contain only the 2 accessible files
- 2 `.gz` files are produced (not 3)
- Return value has `status=COMPLETED_WITH_ERRORS`, `filesProcessed=2`, `filesSkipped` has 1 entry

### AC-4: Single File (from EC-3)

**Given** 1 file exists in `s3://bucket/input/`
**When** the Lambda is invoked
**Then**:
- `.zip` and `.tar` each contain exactly 1 entry
- 1 `.gz` file is produced
- Return value has `filesProcessed=1`

### AC-5: File Name Preservation (from FR-2.3, FR-3.2, FR-4.2)

**Given** a file at `s3://bucket/input/my-report.csv`
**When** the Lambda compresses it
**Then**:
- The zip entry name is `my-report.csv` (not `input/my-report.csv`)
- The tar entry name is `my-report.csv`
- The gzip output key is `output/my-report.csv.gz`

---

## 8. Test Plan

### 8.1 Unit Tests (no AWS/Floci dependency)

| Test | Validates |
|------|-----------|
| `ZipStrategyTest` | Zip archive creation with known bytes, entry names |
| `TarStrategyTest` | Tar archive creation with known bytes, entry names |
| `GzipStrategyTest` | Individual gzip compression, decompression roundtrip |
| `CompressionOrchestratorTest` | All strategies called, results aggregated |

### 8.2 Integration Tests (Floci via Docker Compose / Testcontainers)

| Test | Validates |
|------|-----------|
| `HappyPathIT` | AC-1: Full flow against real S3 (Floci) |
| `EmptyPrefixIT` | AC-2: No files → no outputs |
| `PartialFailureIT` | AC-3: Inaccessible file skipped |
| `SingleFileIT` | AC-4: Single file compressed correctly |

### 8.3 Test Data Files

| File | Content |
|------|---------|
| `sample.txt` | `Hello from Floci! This is a plain text test file.` |
| `report.csv` | 5-row CSV with headers (id, name, value) |
| `config.json` | Small JSON object with nested fields |

---

## 9. Infrastructure (Terraform)

All AWS infrastructure is defined as code using Terraform, ensuring the deployment
is repeatable, version-controlled, and reviewable alongside the application code.

### 9.1 Resources

| Resource | Terraform Resource Type | Purpose |
|----------|------------------------|---------|
| S3 Bucket | `aws_s3_bucket` | Stores input files and compressed outputs |
| S3 Encryption | `aws_s3_bucket_server_side_encryption_configuration` | SSE-S3 (AES256) at rest |
| S3 Bucket Policy | `aws_s3_bucket_policy` | Enforce HTTPS in transit |
| S3 Lifecycle | `aws_s3_bucket_lifecycle_configuration` | Expire old outputs |
| Lambda Function | `aws_lambda_function` | Runs the Quarkus native binary (arm64) |
| Lambda IAM Role | `aws_iam_role` | Execution role for the Lambda |
| IAM Policy | `aws_iam_role_policy` | S3 read/write + scoped CloudWatch Logs |
| EventBridge Rule | `aws_scheduler_schedule` | Daily cron trigger |
| CloudWatch Log Group | `aws_cloudwatch_log_group` | Lambda log retention |
| CloudWatch Alarm | `aws_cloudwatch_metric_alarm` | Alert on Lambda errors |
| SNS Topic | `aws_sns_topic` | Alarm notification target |
| SQS DLQ | `aws_sqs_queue` | Dead letter queue for failed invocations |

### 9.2 Infrastructure Requirements

| ID     | Requirement |
|--------|-------------|
| IR-1.1 | The Lambda IAM role SHALL have least-privilege permissions: `s3:ListBucket`, `s3:GetObject`, `s3:PutObject` scoped to the specific bucket, plus CloudWatch Logs permissions scoped to the Lambda's log group. |
| IR-1.2 | The EventBridge Scheduler SHALL use a cron expression for daily execution at a configurable time (default: `cron(0 2 * * ? *)` — 2:00 AM UTC). |
| IR-1.3 | The Lambda SHALL be configured with 512 MB memory, 120-second timeout, arm64 architecture, and `reserved_concurrent_executions = 1`. |
| IR-1.4 | The CloudWatch Log Group SHALL have a 30-day retention policy. |
| IR-1.5 | All resources SHALL be tagged with `Project=s3-file-compressor` and `ManagedBy=terraform`. |
| IR-1.6 | The S3 bucket SHALL have versioning disabled, no public access, SSE-S3 encryption, HTTPS-only bucket policy, and lifecycle expiration on `output/`. |
| IR-1.7 | A CloudWatch Alarm SHALL trigger an SNS notification when Lambda errors > 0 within a 5-minute period. |
| IR-1.8 | An SQS dead letter queue SHALL capture failed Lambda invocations. |

### 9.3 Terraform Variables

| Variable | Type | Default | Description |
|----------|------|---------|-------------|
| `aws_region` | string | `us-east-1` | AWS region |
| `bucket_name` | string | — (required) | S3 bucket name |
| `input_prefix` | string | `input/` | S3 prefix for source files |
| `output_prefix` | string | `output/` | S3 prefix for compressed outputs |
| `archive_name` | string | `archive` | Base name for .zip/.tar outputs |
| `schedule_expression` | string | `cron(0 2 * * ? *)` | EventBridge cron |
| `lambda_memory_mb` | number | `512` | Lambda memory in MB |
| `lambda_timeout_sec` | number | `120` | Lambda timeout in seconds |
| `log_retention_days` | number | `30` | CloudWatch log retention |

### 9.4 Terraform Outputs

| Output | Value |
|--------|-------|
| `lambda_function_arn` | ARN of the deployed Lambda |
| `s3_bucket_name` | Name of the S3 bucket |
| `eventbridge_schedule_arn` | ARN of the EventBridge schedule |
| `lambda_log_group` | CloudWatch Log Group name |

### 9.5 Terraform File Structure

```
terraform/
├── main.tf          # Provider config, backend
├── s3.tf            # Bucket resource
├── lambda.tf        # Lambda function, IAM role, policy
├── scheduler.tf     # EventBridge Scheduler rule
├── logs.tf          # CloudWatch Log Group
├── variables.tf     # Input variables
├── outputs.tf       # Output values
└── terraform.tfvars.example  # Example variable values
```

---

## 10. Development Phases

| Phase | Tasks | Spec Sections |
|-------|-------|---------------|
| **Phase 1: Scaffold** | Generate Quarkus project, add extensions, docker-compose for Floci | §6 |
| **Phase 2: Models & Config** | Create `FileEntry`, `CompressionResult`, `CompressorConfig` | §3, §4 |
| **Phase 3: Compression Strategies** | Implement Zip, Tar, Gzip strategies + unit tests | §FR-3, FR-4, FR-5 |
| **Phase 4: S3 Service** | Implement list/download/upload with error handling | §FR-1, FR-2, FR-6 |
| **Phase 5: Orchestrator & Handler** | Wire everything together, add summary logging | §FR-7 |
| **Phase 6: Integration Tests** | Full-flow tests against Floci | §7, §8 |
| **Phase 7: Terraform** | Write all infrastructure-as-code modules | §9 (IR-1.1 through IR-1.6) |
| **Phase 8: Native Build & Deploy** | Verify native compilation, package for Lambda | §NFR-2 |
