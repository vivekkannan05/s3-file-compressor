package com.example.compressor;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Shared decompression utilities for tests. Eliminates duplication
 * across strategy tests and integration tests. (DRY)
 */
public final class TestDecompressor {

    private TestDecompressor() {}

    public static Map<String, String> unzip(byte[] zipBytes) throws Exception {
        var entries = new HashMap<String, String>();
        try (var zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        return entries;
    }

    public static Map<String, String> untar(byte[] tarBytes) throws Exception {
        var entries = new HashMap<String, String>();
        try (var tis = new TarArchiveInputStream(new ByteArrayInputStream(tarBytes))) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                if (entry.isFile()) {
                    entries.put(entry.getName(), new String(tis.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        }
        return entries;
    }

    public static String gunzip(byte[] gzBytes) throws Exception {
        try (var gis = new GZIPInputStream(new ByteArrayInputStream(gzBytes))) {
            return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
