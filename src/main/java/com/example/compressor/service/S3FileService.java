package com.example.compressor.service;

import com.example.compressor.config.CompressorConfig;
import com.example.compressor.model.FileEntry;
import com.example.compressor.model.SkippedFile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles all S3 interactions: list, download, upload. (§FR-1, §FR-2, §FR-6)
 */
@ApplicationScoped
public class S3FileService {

    private static final Logger LOG = Logger.getLogger(S3FileService.class);
    private static final long DEFAULT_MAX_TOTAL_BYTES = 100 * 1024 * 1024; // 100 MB

    @Inject
    S3Client s3;

    @Inject
    CompressorConfig config;

    /**
     * Lists all object keys under the configured input prefix. (§FR-1.1, §FR-1.5)
     * Uses pagination to handle prefixes with more than 1000 objects.
     */
    public List<String> listFileKeys() {
        var request = ListObjectsV2Request.builder()
                .bucket(config.bucket())
                .prefix(config.inputPrefix())
                .delimiter("/")
                .build();

        ListObjectsV2Iterable pages = s3.listObjectsV2Paginator(request);

        return pages.contents().stream()
                .map(S3Object::key)
                .filter(key -> !key.equals(config.inputPrefix()))
                .toList();
    }

    /**
     * Downloads files from S3 in parallel using virtual threads, skipping failures. (§FR-2.1, §FR-2.2, §FR-2.4)
     */
    public DownloadResult downloadFiles(List<String> keys) {
        var files = Collections.synchronizedList(new ArrayList<FileEntry>());
        var skipped = Collections.synchronizedList(new ArrayList<SkippedFile>());
        var totalBytes = new AtomicLong(0);

        LOG.infof("Downloading %d files using virtual threads (parallel I/O)", keys.size());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = keys.stream()
                    .map(key -> executor.submit(() ->
                            downloadSingleFile(key, files, skipped, totalBytes)))
                    .toList();

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.error("Download interrupted");
                } catch (ExecutionException e) {
                    LOG.errorf("Unexpected download error: %s", e.getCause().getMessage());
                }
            }
        }

        LOG.infof("Downloads complete: %d succeeded, %d skipped, %d bytes total",
                files.size(), skipped.size(), totalBytes.get());
        return new DownloadResult(new ArrayList<>(files), new ArrayList<>(skipped));
    }

    private void downloadSingleFile(String key, List<FileEntry> files,
                                    List<SkippedFile> skipped, AtomicLong totalBytes) {
        try {
            var request = GetObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(key)
                    .build();

            byte[] content = s3.getObjectAsBytes(request).asByteArray();

            long cumulative = totalBytes.addAndGet(content.length);
            if (cumulative > DEFAULT_MAX_TOTAL_BYTES) {
                LOG.errorf("Memory guard: cumulative size %d bytes exceeds threshold", cumulative);
                skipped.add(new SkippedFile(key, "Cumulative download size exceeds memory threshold"));
                return;
            }

            String name = extractFileName(key);
            String sanitized = sanitizeEntryName(name);
            if (sanitized == null) {
                LOG.warnf("Rejected file with unsafe name: %s", name);
                skipped.add(new SkippedFile(key, "File name rejected: path traversal detected"));
                return;
            }

            files.add(new FileEntry(sanitized, content));
            LOG.infof("Downloaded: %s (%d bytes)", key, content.length);
        } catch (Exception e) {
            LOG.errorf("Failed to download %s: %s", key, e.getMessage());
            skipped.add(new SkippedFile(key, sanitizeErrorMessage(e)));
        }
    }

    /**
     * Uploads a compressed output with the correct Content-Type. (§FR-6.1, §FR-6.3)
     */
    public String uploadFile(String outputKey, byte[] content) {
        String fullKey = config.outputPrefix() + outputKey;
        try {
            String contentType = resolveContentType(outputKey);

            var request = PutObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(fullKey)
                    .contentType(contentType)
                    .build();

            s3.putObject(request, RequestBody.fromBytes(content));
            LOG.infof("Uploaded: %s (%d bytes, %s)", fullKey, content.length, contentType);
            return fullKey;
        } catch (Exception e) {
            LOG.errorf("Failed to upload %s: %s", fullKey, e.getMessage());
            return null;
        }
    }

    /** Strips the input prefix from a key to get the bare file name. (§FR-2.3) */
    private String extractFileName(String key) {
        if (key.startsWith(config.inputPrefix())) {
            return key.substring(config.inputPrefix().length());
        }
        return key;
    }

    /** Zip Slip protection: reject names with path traversal. (§FR-2.5, §NFR-5.6) */
    static String sanitizeEntryName(String name) {
        if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
            return null;
        }
        return name;
    }

    /** Prevents leaking internal AWS details in the Lambda response. (§FR-7.3) */
    static String sanitizeErrorMessage(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "S3 operation failed: unknown error";
        return "S3 operation failed: " + msg.replaceAll("arn:[\\w:/-]+", "[ARN]")
                .replaceAll("\\d{12}", "[ACCOUNT]");
    }

    /** Content-Type mapping via pattern matching switch. (§FR-6.3) */
    static String resolveContentType(String key) {
        return switch (key) {
            case String k when k.endsWith(".zip") -> "application/zip";
            case String k when k.endsWith(".tar") -> "application/x-tar";
            case String k when k.endsWith(".gz")  -> "application/gzip";
            default -> "application/octet-stream";
        };
    }

    public record DownloadResult(List<FileEntry> files, List<SkippedFile> skipped) {}
}
