package com.example.compressor.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.example.compressor.model.CompressionResult;
import com.example.compressor.service.CompressionOrchestrator;
import com.example.compressor.service.S3FileService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * Lambda entry point. Receives EventBridge scheduled event and orchestrates
 * the full read → compress → upload flow. (§FR-7, §4.1, §4.2)
 */
@Named("compressionHandler")
public class CompressionHandler implements RequestHandler<Map<String, Object>, CompressionResult> {

    private static final Logger LOG = Logger.getLogger(CompressionHandler.class);
    private static final long SAFETY_MARGIN_MS = 10_000;

    @Inject
    S3FileService s3FileService;

    @Inject
    CompressionOrchestrator orchestrator;

    @Override
    public CompressionResult handleRequest(Map<String, Object> event, Context context) {
        long startTime = System.currentTimeMillis();
        LOG.info("S3 File Compressor Lambda invoked");

        List<String> keys = s3FileService.listFileKeys();
        int totalFound = keys.size();
        LOG.infof("Found %d files to process", totalFound);

        if (totalFound == 0) {
            return CompressionResult.builder()
                    .durationMs(elapsed(startTime))
                    .build();
        }

        if (isTimeRunningOut(context, startTime)) {
            LOG.warn("Insufficient remaining time — aborting before downloads");
            return CompressionResult.builder()
                    .totalFilesFound(totalFound)
                    .durationMs(elapsed(startTime))
                    .build();
        }

        S3FileService.DownloadResult downloadResult = s3FileService.downloadFiles(keys);
        int processed = downloadResult.files().size();

        if (isTimeRunningOut(context, startTime)) {
            LOG.warn("Insufficient remaining time — aborting before compression");
            return CompressionResult.builder()
                    .totalFilesFound(totalFound)
                    .filesProcessed(processed)
                    .filesSkipped(downloadResult.skipped())
                    .durationMs(elapsed(startTime))
                    .build();
        }

        var orchResult = orchestrator.compressAndUpload(downloadResult.files());

        CompressionResult result = CompressionResult.builder()
                .totalFilesFound(totalFound)
                .filesProcessed(processed)
                .filesSkipped(downloadResult.skipped())
                .strategyFailures(orchResult.strategyFailures())
                .outputsWritten(orchResult.uploadedKeys())
                .durationMs(elapsed(startTime))
                .build();

        LOG.infof("Completed: %s | processed=%d, skipped=%d, strategyFailures=%d, outputs=%d, duration=%dms",
                result.status(), processed, result.filesSkipped().size(),
                result.strategyFailures().size(), result.outputsWritten().size(), result.durationMs());

        return result;
    }

    /** Check if Lambda is about to timeout. (§FR-7.4) */
    private boolean isTimeRunningOut(Context context, long startTime) {
        if (context == null) return false;
        long remaining = context.getRemainingTimeInMillis();
        return remaining < SAFETY_MARGIN_MS;
    }

    private long elapsed(long startTime) {
        return System.currentTimeMillis() - startTime;
    }
}
