package com.frame.dto;

import java.time.Instant;
import java.util.UUID;

public final class ProcessingJobDtos {

    private ProcessingJobDtos() {}

    public record ProcessingJobResponse(
            UUID id,
            UUID videoId,
            String status,
            int progressPercent,
            String stage,
            String errorMessage,
            Instant startedAt,
            Instant completedAt,
            Instant createdAt) {}

    /** Broadcast on /topic/videos/{videoId}/progress as a job advances. */
    public record ProcessingProgressMessage(
            UUID videoId, UUID jobId, String status, int progressPercent, String stage, String message) {}

    /** Broadcast on /topic/videos so list views can update without a per-video subscription. */
    public record VideoStatusBroadcast(UUID videoId, String status) {}
}
