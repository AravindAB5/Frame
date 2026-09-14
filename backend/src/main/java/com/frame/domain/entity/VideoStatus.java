package com.frame.domain.entity;

/**
 * Video lifecycle. Transitions are one-directional except for FAILED, which a
 * reprocess request can push back to QUEUED (see ProcessingOrchestrationService).
 */
public enum VideoStatus {
    UPLOADING,
    QUEUED,
    PROCESSING,
    READY,
    FAILED
}
