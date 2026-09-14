package com.frame.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class EventDtos {

    private EventDtos() {}

    public record EventResponse(
            UUID id,
            UUID videoId,
            String eventType,
            long timestampMs,
            Long endTimestampMs,
            String title,
            String description,
            Map<String, Object> metadata,
            Double confidence,
            Instant createdAt) {}
}
