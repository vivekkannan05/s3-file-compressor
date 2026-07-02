package com.example.compressor.service;

import com.example.compressor.config.CompressorConfig;
import com.example.compressor.model.FileEntry;
import com.example.compressor.model.OutputEntry;
import com.example.compressor.model.StrategyOutcome;
import com.example.compressor.model.StrategyOutcome.Failure;
import com.example.compressor.model.StrategyOutcome.Success;
import com.example.compressor.service.strategy.CompressionStrategy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives all compression strategies and uploads results. (§FR-3, §FR-4, §FR-5, §FR-6, §NFR-6.2)
 * Uses sealed types and pattern matching for exhaustive outcome handling.
 */
@ApplicationScoped
public class CompressionOrchestrator {

    private static final Logger LOG = Logger.getLogger(CompressionOrchestrator.class);

    @Inject
    Instance<CompressionStrategy> strategies;

    @Inject
    S3FileService s3FileService;

    @Inject
    CompressorConfig config;

    /**
     * Runs all compression strategies against the given files and uploads outputs.
     */
    public OrchestratorResult compressAndUpload(List<FileEntry> files) {
        if (files.isEmpty()) {
            LOG.info("No files to compress — skipping.");
            return new OrchestratorResult(List.of(), List.of());
        }

        var uploadedKeys = new ArrayList<String>();
        var failures = new ArrayList<String>();

        for (CompressionStrategy strategy : strategies) {
            LOG.infof("Running %s strategy on %d files", strategy.formatName(), files.size());
            StrategyOutcome outcome = runStrategy(strategy, files);

            switch (outcome) {
                case Success s -> {
                    LOG.infof("Strategy %s: SUCCESS (%d outputs)", s.formatName(), s.outputs().size());
                    for (OutputEntry output : s.outputs()) {
                        String key = s3FileService.uploadFile(output.key(), output.content());
                        if (key != null) {
                            uploadedKeys.add(key);
                        }
                    }
                }
                case Failure f -> {
                    LOG.errorf("Strategy %s: FAILURE (%s)", f.formatName(), f.reason());
                    failures.add(f.formatName() + ": " + f.reason());
                }
            }
        }

        return new OrchestratorResult(uploadedKeys, failures);
    }

    private StrategyOutcome runStrategy(CompressionStrategy strategy, List<FileEntry> files) {
        try {
            List<OutputEntry> outputs = strategy.compress(files, config.archiveName());
            return new Success(strategy.formatName(), outputs);
        } catch (Exception e) {
            return new Failure(strategy.formatName(), e.getMessage());
        }
    }

    public record OrchestratorResult(List<String> uploadedKeys, List<String> strategyFailures) {}
}
