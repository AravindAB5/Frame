package com.frame.dto;

import java.time.Instant;
import java.util.UUID;

public final class VideoDtos {

    private VideoDtos() {}

    public record VideoResponse(
            UUID id,
            String title,
            String originalFilename,
            String contentType,
            long sizeBytes,
            Double durationSeconds,
            String status,
            boolean hasThumbnail,
            Instant createdAt,
            Instant updatedAt) {}
}
