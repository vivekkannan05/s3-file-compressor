#!/usr/bin/env bash
#
# FLOCI LOCAL EMULATOR ONLY -- DO NOT USE WITH REAL AWS
#
# Seeds test data into the local Floci S3 emulator (§8.3 Test Data Files).
# The credentials below are throwaway values accepted by Floci.
# They grant no access to any real AWS account.
#
set -euo pipefail

ENDPOINT="http://localhost:4566"
BUCKET="my-bucket"
INPUT_PREFIX="input"
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export AWS_DEFAULT_REGION=us-east-1

echo "==> Creating bucket: ${BUCKET}"
aws s3 mb "s3://${BUCKET}" --endpoint-url "${ENDPOINT}" 2>/dev/null || echo "    (bucket already exists)"

echo "==> Uploading sample.txt"
echo "Hello from Floci! This is a plain text test file." \
  | aws s3 cp - "s3://${BUCKET}/${INPUT_PREFIX}/sample.txt" --endpoint-url "${ENDPOINT}"

echo "==> Uploading report.csv"
cat <<'CSV' | aws s3 cp - "s3://${BUCKET}/${INPUT_PREFIX}/report.csv" --endpoint-url "${ENDPOINT}"
id,name,value
1,alpha,100
2,beta,200
3,gamma,300
4,delta,400
5,epsilon,500
CSV

echo "==> Uploading config.json"
cat <<'JSON' | aws s3 cp - "s3://${BUCKET}/${INPUT_PREFIX}/config.json" --endpoint-url "${ENDPOINT}"
{
  "appName": "s3-file-compressor",
  "version": "1.0.0",
  "settings": {
    "compression": {
      "formats": ["zip", "tar", "gz"],
      "level": "default"
    },
    "schedule": "daily"
  }
}
JSON

echo "==> Verifying uploaded files:"
aws s3 ls "s3://${BUCKET}/${INPUT_PREFIX}/" --endpoint-url "${ENDPOINT}"

echo "==> Done. Test data seeded successfully."
