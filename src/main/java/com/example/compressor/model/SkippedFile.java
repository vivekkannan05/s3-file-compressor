package com.example.compressor.model;

/**
 * Records a file that was skipped during processing. (§FR-2.2, §4.3)
 *
 * @param key    the original S3 key
 * @param reason human-readable failure reason
 */
public record SkippedFile(String key, String reason) {
}
