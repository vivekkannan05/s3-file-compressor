package com.example.compressor.service.strategy;

import com.example.compressor.model.FileEntry;
import com.example.compressor.model.OutputEntry;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Creates a single .tar archive containing all files. (§FR-4)
 */
@ApplicationScoped
public class TarStrategy implements CompressionStrategy {

    @Override
    public List<OutputEntry> compress(List<FileEntry> files, String archiveName) {
        int estimatedSize = files.stream().mapToInt(f -> f.content().length + 512).sum();
        try (var baos = new ByteArrayOutputStream(estimatedSize);
             var tar = new TarArchiveOutputStream(baos)) {

            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

            for (FileEntry file : files) {
                var entry = new TarArchiveEntry(file.name());
                entry.setSize(file.content().length);
                tar.putArchiveEntry(entry);
                tar.write(file.content());
                tar.closeArchiveEntry();
            }
            tar.finish();
            tar.flush();

            return List.of(new OutputEntry(archiveName + ".tar", baos.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to create tar archive", e);
        }
    }

    @Override
    public String formatName() {
        return "tar";
    }
}
