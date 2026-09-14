package com.frame.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;
import org.springframework.core.io.Resource;

/**
 * Abstraction over "where video bytes live". {@link LocalStorageService} is the only
 * implementation today; the interface exists so an S3-backed implementation can be swapped in
 * later purely via a new {@code @Bean} — no caller changes.
 */
public interface StorageService {

    record StoredFile(String storagePath, long sizeBytes) {}

    StoredFile store(UUID videoId, String originalFilename, InputStream content) throws IOException;

    Resource loadAsResource(String storagePath);

    void delete(String storagePath);
}
