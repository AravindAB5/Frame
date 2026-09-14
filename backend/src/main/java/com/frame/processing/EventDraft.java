package com.frame.processing;

import com.frame.domain.entity.EventType;
import java.util.Map;

/** What a processor emits for one detected event; persistence details (id, videoId, createdAt) are the context's job. */
public record EventDraft(
        EventType eventType,
        long timestampMs,
        Long endTimestampMs,
        String title,
        String description,
        Map<String, Object> metadata,
        Double confidence) {

    public static EventDraft instant(EventType type, long timestampMs, String title, Map<String, Object> metadata, double confidence) {
        return new EventDraft(type, timestampMs, null, title, null, metadata, confidence);
    }

    public static EventDraft range(EventType type, long startMs, long endMs, String title, String description) {
        return new EventDraft(type, startMs, endMs, title, description, Map.of(), null);
    }
}
