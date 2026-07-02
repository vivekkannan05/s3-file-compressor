package com.example.compressor.handler;

import com.example.compressor.TestDecompressor;
import com.example.compressor.model.CompressionResult;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import jakarta.inject.Inject;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests running the full Lambda handler against Floci S3.
 * Validates AC-1 (happy path), AC-2 (empty prefix), AC-4 (single file).
 */
@QuarkusTest
class CompressionHandlerTest {

    private static final String BUCKET = "my-bucket";

    @Inject
    S3Client s3;

    @Inject
    CompressionHandler handler;

    @BeforeEach
    void setUp() {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (Exception ignored) {
        }
        cleanPrefix("input/");
        cleanPrefix("output/");
    }

    @Test
    void happyPath_threeFiles_producesAllOutputs() throws Exception {
        seedFile("input/sample.txt", "Hello from Floci!");
        seedFile("input/report.csv", "id,name\n1,alpha");
        seedFile("input/config.json", "{\"key\":\"value\"}");

        CompressionResult result = handler.handleRequest(Map.of(), null);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalFilesFound()).isEqualTo(3);
        assertThat(result.filesProcessed()).isEqualTo(3);
        assertThat(result.filesSkipped()).isEmpty();
        assertThat(result.strategyFailures()).isEmpty();
        assertThat(result.durationMs()).isGreaterThan(0);

        assertThat(result.outputsWritten()).hasSize(5);

        byte[] zipBytes = downloadOutput("output/archive.zip");
        Map<String, String> zipEntries = TestDecompressor.unzip(zipBytes);
        assertThat(zipEntries).hasSize(3);
        assertThat(zipEntries).containsKeys("sample.txt", "report.csv", "config.json");
        assertThat(zipEntries.get("sample.txt")).isEqualTo("Hello from Floci!");

        byte[] tarBytes = downloadOutput("output/archive.tar");
        Map<String, String> tarEntries = TestDecompressor.untar(tarBytes);
        assertThat(tarEntries).hasSize(3);

        String decompressed = TestDecompressor.gunzip(downloadOutput("output/sample.txt.gz"));
        assertThat(decompressed).isEqualTo("Hello from Floci!");
    }

    @Test
    void emptyPrefix_producesNoOutputs() {
        CompressionResult result = handler.handleRequest(Map.of(), null);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.totalFilesFound()).isEqualTo(0);
        assertThat(result.filesProcessed()).isEqualTo(0);
        assertThat(result.outputsWritten()).isEmpty();
    }

    @Test
    void singleFile_producesThreeOutputs() throws Exception {
        seedFile("input/only.txt", "single file");

        CompressionResult result = handler.handleRequest(Map.of(), null);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.filesProcessed()).isEqualTo(1);
        assertThat(result.outputsWritten()).hasSize(3);

        Map<String, String> zipEntries = TestDecompressor.unzip(downloadOutput("output/archive.zip"));
        assertThat(zipEntries).hasSize(1);
        assertThat(zipEntries).containsKey("only.txt");
    }

    private void seedFile(String key, String content) {
        s3.putObject(
                PutObjectRequest.builder().bucket(BUCKET).key(key).build(),
                RequestBody.fromString(content));
    }

    private byte[] downloadOutput(String key) {
        return s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(BUCKET).key(key).build()
        ).asByteArray();
    }

    private void cleanPrefix(String prefix) {
        try {
            var listing = s3.listObjectsV2(b -> b.bucket(BUCKET).prefix(prefix));
            for (var obj : listing.contents()) {
                s3.deleteObject(b -> b.bucket(BUCKET).key(obj.key()));
            }
        } catch (Exception ignored) {
        }
    }
}
