package com.frame.processing;

/**
 * Pluggable video-analysis strategy. {@link MockEventProcessor} is the only implementation today;
 * a real implementation (ffmpeg scene detection, an AI transcription/OCR pipeline, ...) is a
 * drop-in {@code @Bean} behind this same interface — selected via {@code frame.processing.provider}.
 */
public interface EventProcessor {

    void process(ProcessingContext context) throws ProcessingException;
}
