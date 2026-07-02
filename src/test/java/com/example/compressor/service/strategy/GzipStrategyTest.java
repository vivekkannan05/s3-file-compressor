package com.example.compressor.service.strategy;

import com.example.compressor.TestDecompressor;
import com.example.compressor.model.FileEntry;
import com.example.compressor.model.OutputEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GzipStrategyTest {

    private GzipStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new GzipStrategy();
    }

    @Test
    void shouldCreateOneGzPerFile() {
        var files = List.of(
                new FileEntry("sample.txt", "hello".getBytes(StandardCharsets.UTF_8)),
                new FileEntry("report.csv", "id,name".getBytes(StandardCharsets.UTF_8))
        );

        List<OutputEntry> outputs = strategy.compress(files, "archive");

        assertThat(outputs).hasSize(2);
        assertThat(outputs.get(0).key()).isEqualTo("sample.txt.gz");
        assertThat(outputs.get(1).key()).isEqualTo("report.csv.gz");
    }

    @Test
    void shouldRoundtripDecompress() throws Exception {
        var original = "Hello from Floci! This is a plain text test file.";
        var files = List.of(
                new FileEntry("sample.txt", original.getBytes(StandardCharsets.UTF_8))
        );

        List<OutputEntry> outputs = strategy.compress(files, "archive");
        String decompressed = TestDecompressor.gunzip(outputs.getFirst().content());

        assertThat(decompressed).isEqualTo(original);
    }

    @Test
    void shouldUseOriginalFileNameWithGzSuffix() {
        var files = List.of(
                new FileEntry("my-report.csv", "data".getBytes(StandardCharsets.UTF_8))
        );

        List<OutputEntry> outputs = strategy.compress(files, "archive");

        assertThat(outputs.getFirst().key()).isEqualTo("my-report.csv.gz");
    }

    @Test
    void shouldHandleEmptyFileList() {
        List<OutputEntry> outputs = strategy.compress(List.of(), "archive");
        assertThat(outputs).isEmpty();
    }

    @Test
    void formatNameShouldBeGzip() {
        assertThat(strategy.formatName()).isEqualTo("gzip");
    }
}
