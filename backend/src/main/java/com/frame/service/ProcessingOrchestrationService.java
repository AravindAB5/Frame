package com.frame.service;

import com.frame.domain.entity.JobStatus;
import com.frame.domain.entity.ProcessingJob;
import com.frame.domain.entity.Video;
import com.frame.domain.entity.VideoStatus;
import com.frame.dto.ProcessingJobDtos.VideoStatusBroadcast;
import com.frame.repository.ProcessingJobRepository;
import com.frame.repository.VideoRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Entry point used by {@link VideoService} right after an upload is stored: creates the QUEUED
 * job row and hands off to {@link ProcessingWorker} for the actual async run. See
 * {@link ProcessingWorker} for why the async execution lives in its own bean.
 */
@Service
public class ProcessingOrchestrationService {

    private final VideoRepository videoRepository;
    private final ProcessingJobRepository jobRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ProcessingWorker processingWorker;

    public ProcessingOrchestrationService(
            VideoRepository videoRepository,
            ProcessingJobRepository jobRepository,
            SimpMessagingTemplate messagingTemplate,
            ProcessingWorker processingWorker) {
        this.videoRepository = videoRepository;
        this.jobRepository = jobRepository;
        this.messagingTemplate = messagingTemplate;
        this.processingWorker = processingWorker;
    }

    public void enqueue(Video video) {
        video.setStatus(VideoStatus.QUEUED);
        videoRepository.save(video);
        messagingTemplate.convertAndSend("/topic/videos", new VideoStatusBroadcast(video.getId(), VideoStatus.QUEUED.name()));

        ProcessingJob job = ProcessingJob.builder()
                .videoId(video.getId())
                .status(JobStatus.QUEUED)
                .progressPercent(0)
                .build();
        job = jobRepository.save(job);

        processingWorker.runAsync(video.getId(), job.getId());
    }
}
