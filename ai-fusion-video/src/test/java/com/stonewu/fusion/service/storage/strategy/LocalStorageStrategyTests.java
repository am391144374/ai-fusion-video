package com.stonewu.fusion.service.storage.strategy;

import com.stonewu.fusion.entity.storage.StorageConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageStrategyTests {

    @TempDir
    Path tempDir;

    @Test
    void deletesManagedMediaFile() throws Exception {
        Path video = tempDir.resolve("videos/old.mp4");
        Files.createDirectories(video.getParent());
        Files.writeString(video, "video");
        StorageConfig config = StorageConfig.builder().type("local").basePath(tempDir.toString()).build();

        boolean deleted = new LocalStorageStrategy().delete("/media/videos/old.mp4", config);

        assertThat(deleted).isTrue();
        assertThat(video).doesNotExist();
    }

    @Test
    void rejectsFileOutsideManagedMediaPath() throws Exception {
        Path outside = tempDir.getParent().resolve("outside.mp4");
        Files.writeString(outside, "video");
        StorageConfig config = StorageConfig.builder().type("local").basePath(tempDir.toString()).build();

        boolean deleted = new LocalStorageStrategy().delete("/media/../outside.mp4", config);

        assertThat(deleted).isFalse();
        assertThat(outside).exists();
        Files.deleteIfExists(outside);
    }

    @Test
    void rejectsAbsoluteExternalUrlEvenWhenPathStartsWithMedia() throws Exception {
        Path video = tempDir.resolve("videos/old.mp4");
        Files.createDirectories(video.getParent());
        Files.writeString(video, "video");
        StorageConfig config = StorageConfig.builder().type("local").basePath(tempDir.toString()).build();

        boolean deleted = new LocalStorageStrategy().delete(
                "https://foreign.example.com/media/videos/old.mp4", config);

        assertThat(deleted).isFalse();
        assertThat(video).exists();
}
