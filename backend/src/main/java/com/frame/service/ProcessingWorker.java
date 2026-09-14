package com.frame.service;

import com.frame.domain.entity.Event;
import com.frame.domain.entity.JobStatus;
import com.frame.domain.entity.Video;
import com.frame.domain.entity.VideoStatus;
import com.frame.dto.ProcessingJobDtos.ProcessingProgressMessage;
import com.frame.dto.ProcessingJobDtos.VideoStatusBroadcast;
import com.frame.processing.EventDraft;
import com.frame.processing.EventProcessor;
import com.frame.processing.ProcessingContext;
import com.frame.processing.ProcessingException;
import com.frame.repository.EventRepository;
import com.frame.repository.ProcessingJobRepository;
import com.frame.repository.VideoRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs one processing job on the bounded {@code processingExecutor} pool and keeps
 * Video.status / ProcessingJob.status / WebSocket subscribers in sync as it progresses.
 *
 * This is a separate bean (rather than a method on {@link ProcessingOrchestrationService}) so the
 * {@code @Async} entry point is always invoked through the Spring proxy — calling an {@code @Async}
 * method on {@code this} from within the same class silently runs it synchronously, since
 * self-invocation bypasses the AOP proxy entirely.
 */
@Service
public class ProcessingWorker {

    private static final Logger log = LoggerFactory.getLogger(ProcessingWorker.class);

    private final VideoRepository videoRepository;
    private final ProcessingJobRepository jobRepository;
    private final EventRepository eventRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EventProcessor eventProcessor;

    public ProcessingWorker(
            VideoRepository videoRepository,
            ProcessingJobRepository jobRepository,
            EventRepository eventRepository,
            SimpMessagingTemplate messagingTemplate,
            EventProcessor eventProcessor) {
        this.videoRepository = videoRepository;
        this.jobRepository = jobRepository;
        this.eventRepository = eventRepository;
        this.messagingTemplate = messagingTemplate;
        this.eventProcessor = eventProcessor;
    }

    @Async("processingExecutor")
    public void runAsync(UUID videoId, UUID jobId) {
        Video video = videoRepository.findById(videoId).orElse(null);
        if (video == null) {
            log.warn("Video {} disappeared before processing job {} could start", videoId, jobId);
            return;
        }

        markRunning(jobId, video);

        DefaultProcessingContext ctx = new DefaultProcessingContext(video, jobId);
        try {
            eventProcessor.process(ctx);
            markCompleted(jobId, video);
        } catch (ProcessingException | RuntimeException ex) {
            log.error("Processing failed for video {} (job {})", videoId, jobId, ex);
            markFailed(jobId, video, ex.getMessage());
        }
    }

    private void markRunning(UUID jobId, Video video) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            jobRepository.save(job);
        });
        video.setStatus(VideoStatus.PROCESSING);
        videoRepository.save(video);
        broadcastVideoStatus(video.getId(), VideoStatus.PROCESSING);
        broadcastProgress(video.getId(), jobId, JobStatus.RUNNING, 0, "Starting", null);
    }

    private void markCompleted(UUID jobId, Video video) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.COMPLETED);
            job.setProgressPercent(100);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        });
        video.setStatus(VideoStatus.READY);
        videoRepository.save(video);
        broadcastVideoStatus(video.getId(), VideoStatus.READY);
        broadcastProgress(video.getId(), jobId, JobStatus.COMPLETED, 100, "Ready", null);
    }

    private void markFailed(UUID jobId, Video video, String errorMessage) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            job.setCompletedAt(Instant.now());
            jobRepository.save(job);
        });
        video.setStatus(VideoStatus.FAILED);
        videoRepository.save(video);
        broadcastVideoStatus(video.getId(), VideoStatus.FAILED);
        broadcastProgress(video.getId(), jobId, JobStatus.FAILED, 0, "Failed", errorMessage);
    }

    private void broadcastVideoStatus(UUID videoId, VideoStatus status) {
        messagingTemplate.convertAndSend("/topic/videos", new VideoStatusBroadcast(videoId, status.name()));
    }

    private void broadcastProgress(UUID videoId, UUID jobId, JobStatus status, int percent, String stage, String message) {
        messagingTemplate.convertAndSend(
                "/topic/videos/" + videoId + "/progress",
                new ProcessingProgressMessage(videoId, jobId, status.name(), percent, stage, message));
    }

    /** Glue between the pure {@link EventProcessor} contract and this worker's persistence/WS concerns. */
    private class DefaultProcessingContext implements ProcessingContext {

        private final Video video;
        private final UUID jobId;

        DefaultProcessingContext(Video video, UUID jobId) {
            this.video = video;
            this.jobId = jobId;
        }

        @Override
        public UUID videoId() {
            return video.getId();
        }

        @Override
        public double durationSeconds() {
            return video.getDurationSeconds() == null ? 0 : video.getDurationSeconds();
        }

        @Override
        public void reportProgress(int percent, String stage) {
            jobRepository.findById(jobId).ifPresent(job -> {
                job.setProgressPercent(percent);
                job.setStage(stage);
                jobRepository.save(job);
            });
            broadcastProgress(video.getId(), jobId, JobStatus.RUNNING, percent, stage, null);
        }

        @Override
        public void emitEvent(EventDraft draft) {
            Event event = Event.builder()
                    .videoId(video.getId())
                    .eventType(draft.eventType())
                    .timestampMs(draft.timestampMs())
                    .endTimestampMs(draft.endTimestampMs())
                    .title(draft.title())
                    .description(draft.description())
                    .metadata(draft.metadata() == null ? Map.of() : draft.metadata())
                    .confidence(draft.confidence())
                    .build();
            eventRepository.save(event);
        }
    }
}
