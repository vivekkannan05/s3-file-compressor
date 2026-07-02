package com.example.compressor.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Type-safe configuration mapping for the compressor. (§NFR-3)
 *
 * Reads from application.properties / environment variables:
 *   COMPRESSOR_BUCKET, COMPRESSOR_INPUT_PREFIX, etc.
 */
@ConfigMapping(prefix = "compressor")
public interface CompressorConfig {

    String bucket();

    String inputPrefix();

    String outputPrefix();

    @WithDefault("archive")
    String archiveName();
}
