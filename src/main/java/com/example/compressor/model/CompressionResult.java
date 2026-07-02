package com.example.compressor.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The Lambda return value summarizing the entire compression run. (§4.2, §4.3, §FR-7)
 * Uses a builder to safely construct with auto-derived status.
 */
public record CompressionResult(
        String status,
        int totalFilesFound,
        int filesProcessed,
        List<SkippedFile> filesSkipped,
        List<String> strategyFailures,
        List<String> outputsWritten,
        long durationMs
) {

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_COMPLETED_WITH_ERRORS = "COMPLETED_WITH_ERRORS";

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int totalFilesFound;
        private int filesProcessed;
        private List<SkippedFile> filesSkipped = List.of();
        private List<String> strategyFailures = List.of();
        private List<String> outputsWritten = List.of();
        private long durationMs;

        private Builder() {}

        public Builder totalFilesFound(int val) { totalFilesFound = val; return this; }
        public Builder filesProcessed(int val) { filesProcessed = val; return this; }
        public Builder filesSkipped(List<SkippedFile> val) { filesSkipped = val; return this; }
        public Builder strategyFailures(List<String> val) { strategyFailures = val; return this; }
        public Builder outputsWritten(List<String> val) { outputsWritten = val; return this; }
        public Builder durationMs(long val) { durationMs = val; return this; }

        /** Status is auto-derived: COMPLETED if no skips and no strategy failures. */
        public CompressionResult build() {
            boolean hasErrors = !filesSkipped.isEmpty() || !strategyFailures.isEmpty();
            String status = hasErrors ? STATUS_COMPLETED_WITH_ERRORS : STATUS_COMPLETED;
            return new CompressionResult(status, totalFilesFound, filesProcessed,
                    List.copyOf(filesSkipped), List.copyOf(strategyFailures),
                    List.copyOf(outputsWritten), durationMs);
        }
    }
}
