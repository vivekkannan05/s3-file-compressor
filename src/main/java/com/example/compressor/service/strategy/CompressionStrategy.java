package com.example.compressor.service.strategy;

import com.example.compressor.model.FileEntry;
import com.example.compressor.model.OutputEntry;

import java.util.List;

/**
 * Pluggable compression strategy. Each implementation produces
 * one or more output entries from the given source files.
 */
public interface CompressionStrategy {

    /**
     * Compress the given files.
     *
     * @param files       downloaded source files
     * @param archiveName base name for archive outputs (e.g., "archive")
     * @return one or more compressed output entries
     */
    List<OutputEntry> compress(List<FileEntry> files, String archiveName);

    /** Human-readable format name for logging. */
    String formatName();
}
