package com.frame.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

/** Stores video files on a local (or Docker-volume-mounted) directory, one subfolder per video. */
@Service
public class LocalStorageService implements StorageService {

    private final Path rootPath;

    public LocalStorageService(@Value("${frame.storage.root-path}") String rootPath) {
        this.rootPath = Path.of(rootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootPath);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create storage root at " + this.rootPath, e);
        }
    }

    @Override
    public StoredFile store(UUID videoId, String originalFilename, InputStream content) throws IOException {
        Path videoDir = rootPath.resolve(videoId.toString());
        Files.createDirectories(videoDir);

        String safeName = sanitize(originalFilename);
        Path target = videoDir.resolve(safeName);
        long bytesCopied = Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);

        String relativePath = rootPath.relativize(target).toString().replace('\\', '/');
        return new StoredFile(relativePath, bytesCopied);
    }

    @Override
    public Resource loadAsResource(String storagePath) {
        try {
            Path file = rootPath.resolve(storagePath).normalize();
            if (!file.startsWith(rootPath)) {
                throw new SecurityException("Attempted path traversal: " + storagePath);
            }
            return new UrlResource(file.toUri());
        } catch (Exception e) {
            throw new UncheckedIOException(new IOException("Could not load stored file: " + storagePath, e));
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Path file = rootPath.resolve(storagePath).normalize();
            if (file.startsWith(rootPath)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String sanitize(String filename) {
        String base = filename == null ? "upload" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        return UUID.randomUUID() + "-" + base;
    }
}
