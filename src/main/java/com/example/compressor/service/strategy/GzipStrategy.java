package com.example.compressor.service.strategy;

import com.example.compressor.model.FileEntry;
import com.example.compressor.model.OutputEntry;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Creates one .gz file per source file. (§FR-5)
 */
@ApplicationScoped
public class GzipStrategy implements CompressionStrategy {

    @Override
    public List<OutputEntry> compress(List<FileEntry> files, String archiveName) {
        var outputs = new ArrayList<OutputEntry>(files.size());

        for (FileEntry file : files) {
            try (var baos = new ByteArrayOutputStream(file.content().length);
                 var gzos = new GZIPOutputStream(baos)) {

                gzos.write(file.content());
                gzos.finish();
                gzos.flush();

                outputs.add(new OutputEntry(file.name() + ".gz", baos.toByteArray()));
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to gzip file: " + file.name(), e);
            }
        }

        return outputs;
    }

    @Override
    public String formatName() {
        return "gzip";
    }
}
