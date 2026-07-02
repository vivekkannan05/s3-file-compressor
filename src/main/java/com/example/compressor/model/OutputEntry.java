package com.example.compressor.model;

/**
 * A compressed output ready to be uploaded to S3. (§FR-6)
 *
 * @param key     the S3 key (relative to output prefix) e.g., "archive.zip"
 * @param content the compressed bytes
 */
public record OutputEntry(String key, byte[] content) {
}
