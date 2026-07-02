package com.example.compressor.service.strategy;

import com.example.compressor.model.FileEntry;
import com.example.compressor.model.OutputEntry;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a single .zip archive containing all files. (§FR-3)
 */
@ApplicationScoped
public class ZipStrategy implements CompressionStrategy {

    @Override
    public List<OutputEntry> compress(List<FileEntry> files, String archiveName) {
        int estimatedSize = files.stream().mapToInt(f -> f.content().length).sum();
        try (var baos = new ByteArrayOutputStream(estimatedSize);
             var zos = new ZipOutputStream(baos)) {

            for (FileEntry file : files) {
                var entry = new ZipEntry(file.name());
                zos.putNextEntry(entry);
                zos.write(file.content());
                zos.closeEntry();
            }
            zos.finish();
            zos.flush();

            return List.of(new OutputEntry(archiveName + ".zip", baos.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create zip archive", e);
        }
    }

    @Override
    public String formatName() {
        return "zip";
    }
}
