package com.example.compressor.service.strategy;

import com.example.compressor.TestDecompressor;
import com.example.compressor.model.FileEntry;
import com.example.compressor.model.OutputEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ZipStrategyTest {

    private ZipStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ZipStrategy();
    }

    @Test
    void shouldCreateZipWithAllFiles() throws Exception {
        var files = List.of(
                new FileEntry("sample.txt", "hello".getBytes(StandardCharsets.UTF_8)),
                new FileEntry("report.csv", "id,name\n1,alpha".getBytes(StandardCharsets.UTF_8))
        );

        List<OutputEntry> outputs = strategy.compress(files, "archive");

        assertThat(outputs).hasSize(1);
        assertThat(outputs.getFirst().key()).isEqualTo("archive.zip");

        Map<String, String> entries = TestDecompressor.unzip(outputs.getFirst().content());
        assertThat(entries).hasSize(2);
        assertThat(entries).containsKeys("sample.txt", "report.csv");
        assertThat(entries.get("sample.txt")).isEqualTo("hello");
        assertThat(entries.get("report.csv")).isEqualTo("id,name\n1,alpha");
    }

    @Test
    void shouldUseOriginalFileNameNotPrefix() throws Exception {
        var files = List.of(
                new FileEntry("my-report.csv", "data".getBytes(StandardCharsets.UTF_8))
        );

        List<OutputEntry> outputs = strategy.compress(files, "archive");
        Map<String, String> entries = TestDecompressor.unzip(outputs.getFirst().content());

        assertThat(entries).containsKey("my-report.csv");
    }

    @Test
    void shouldHandleSingleFile() throws Exception {
        var files = List.of(
                new FileEntry("only.txt", "content".getBytes(StandardCharsets.UTF_8))
        );

        List<OutputEntry> outputs = strategy.compress(files, "archive");
        Map<String, String> entries = TestDecompressor.unzip(outputs.getFirst().content());

        assertThat(entries).hasSize(1);
        assertThat(entries.get("only.txt")).isEqualTo("content");
    }

    @Test
    void shouldHandleEmptyFileList() {
        List<OutputEntry> outputs = strategy.compress(List.of(), "archive");

        assertThat(outputs).hasSize(1);
        assertThat(outputs.getFirst().key()).isEqualTo("archive.zip");
    }

    @Test
    void formatNameShouldBeZip() {
        assertThat(strategy.formatName()).isEqualTo("zip");
    }
}
