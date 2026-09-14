package com.frame.service;

import com.frame.domain.entity.ProcessingJob;
import com.frame.dto.ProcessingJobDtos.ProcessingJobResponse;
import com.frame.exception.NotFoundException;
import com.frame.repository.ProcessingJobRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProcessingJobService {

    private final ProcessingJobRepository jobRepository;

    public ProcessingJobService(ProcessingJobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /** Video ownership must already be verified by the caller (see VideoService#getOwned). */
    public List<ProcessingJobResponse> history(UUID videoId) {
        return jobRepository.findByVideoIdOrderByCreatedAtDesc(videoId).stream().map(this::toResponse).toList();
    }

    public ProcessingJobResponse latest(UUID videoId) {
        ProcessingJob job = jobRepository.findFirstByVideoIdOrderByCreatedAtDesc(videoId)
                .orElseThrow(() -> new NotFoundException("No processing job found for this video"));
        return toResponse(job);
    }

    private ProcessingJobResponse toResponse(ProcessingJob job) {
        return new ProcessingJobResponse(
                job.getId(),
                job.getVideoId(),
                job.getStatus().name(),
                job.getProgressPercent(),
                job.getStage(),
                job.getErrorMessage(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getCreatedAt());
    }
}
