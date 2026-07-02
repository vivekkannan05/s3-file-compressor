package com.example.compressor.model;

/**
 * Represents a file downloaded from S3 with its name and raw content. (§FR-2.3)
 *
 * @param name    the file name without the S3 prefix (e.g., "report.csv")
 * @param content the raw bytes of the file
 */
public record FileEntry(String name, byte[] content) {
}
