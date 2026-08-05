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

class TarGzStrategyTest {

    private TarGzStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new TarGzStrategy();
    }

    @Test
    void shouldCreateTarGzWithAllFiles() throws Exception {
        var files = List.of(
                new FileEntry("sample.txt", "hello".getBytes(StandardCharsets.UTF_8)),
                new FileEntry("report.csv", "id,name\n1,alpha".getBytes(StandardCharsets.UTF_8))
        );

        List<OutputEntry> outputs = strategy.compress(files, "archive");

        assertThat(outputs).hasSize(1);
        assertThat(outputs.getFirst().key()).isEqualTo("archive.tar.gz");

        Map<String, String> entries = TestDecompressor.untargz(outputs.getFirst().content());
        assertThat(entries).hasSize(2);
        assertThat(entries).containsKeys("sample.txt", "report.csv");
        assertThat(entries.get("sample.txt")).isEqualTo("hello");
        assertThat(entries.get("report.csv")).isEqualTo("id,name\n1,alpha");
    }

    @Test
    void shouldPreserveOriginalFileNames() throws Exception {
        var files = List.of(
                new FileEntry("my-report.csv", "data".getBytes(StandardCharsets.UTF_8))
        );

        List<OutputEntry> outputs = strategy.compress(files, "archive");
        Map<String, String> entries = TestDecompressor.untargz(outputs.getFirst().content());

        assertThat(entries).containsKey("my-report.csv");
    }

    @Test
    void shouldHandleSingleFile() throws Exception {
        var files = List.of(
                new FileEntry("only.txt", "content".getBytes(StandardCharsets.UTF_8))
        );

        List<OutputEntry> outputs = strategy.compress(files, "archive");
        Map<String, String> entries = TestDecompressor.untargz(outputs.getFirst().content());

        assertThat(entries).hasSize(1);
        assertThat(entries.get("only.txt")).isEqualTo("content");
    }

    @Test
    void outputShouldBeSmallerThanPlainTar() throws Exception {
        var largeContent = "x".repeat(10_000).getBytes(StandardCharsets.UTF_8);
        var files = List.of(new FileEntry("big.txt", largeContent));

        var tarGzOutputs = strategy.compress(files, "archive");
        var tarOutputs = new TarStrategy().compress(files, "archive");

        assertThat(tarGzOutputs.getFirst().content().length)
                .isLessThan(tarOutputs.getFirst().content().length);
    }

    @Test
    void formatNameShouldBeTarGz() {
        assertThat(strategy.formatName()).isEqualTo("tar.gz");
    }
}
