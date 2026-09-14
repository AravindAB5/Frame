package com.frame.service;

import com.frame.domain.entity.Video;
import com.frame.domain.entity.VideoStatus;
import com.frame.dto.VideoDtos.VideoResponse;
import com.frame.exception.NotFoundException;
import com.frame.repository.VideoRepository;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoService {

    private final VideoRepository videoRepository;
    private final StorageService storageService;
    private final ProcessingOrchestrationService processingOrchestrationService;

    public VideoService(
            VideoRepository videoRepository,
            StorageService storageService,
            ProcessingOrchestrationService processingOrchestrationService) {
        this.videoRepository = videoRepository;
        this.storageService = storageService;
        this.processingOrchestrationService = processingOrchestrationService;
    }

    /**
     * Stores the file and inserts the row, then hands off to processing. Deliberately not
     * {@code @Transactional}: file I/O has no place inside a DB transaction, and the video row
     * must be committed (each repository call commits independently) before the async worker
     * tries to read it back on another thread.
     */
    public VideoResponse upload(UUID ownerId, String title, Double durationSeconds, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }

        UUID storageKey = UUID.randomUUID();
        StorageService.StoredFile stored = storageService.store(storageKey, file.getOriginalFilename(), file.getInputStream());

        Video video = Video.builder()
                .ownerId(ownerId)
                .title(StringUtils.hasText(title) ? title : file.getOriginalFilename())
                .originalFilename(file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename())
                .storagePath(stored.storagePath())
                .contentType(StringUtils.hasText(file.getContentType()) ? file.getContentType() : "video/mp4")
                .sizeBytes(stored.sizeBytes())
                .durationSeconds(durationSeconds)
                .status(VideoStatus.UPLOADING)
                .build();
        video = videoRepository.save(video);

        processingOrchestrationService.enqueue(video);

        return toResponse(video);
    }

    public List<VideoResponse> listForOwner(UUID ownerId) {
        return videoRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(this::toResponse).toList();
    }

    public VideoResponse get(UUID videoId, UUID ownerId) {
        return toResponse(getOwned(videoId, ownerId));
    }

    /** Returns the entity, scoped to its owner. 404 (not 403) on mismatch so we don't leak existence. */
    public Video getOwned(UUID videoId, UUID ownerId) {
        Video video = videoRepository.findById(videoId).orElseThrow(() -> new NotFoundException("Video not found"));
        if (!video.getOwnerId().equals(ownerId)) {
            throw new NotFoundException("Video not found");
        }
        return video;
    }

    public void delete(UUID videoId, UUID ownerId) {
        Video video = getOwned(videoId, ownerId);
        storageService.delete(video.getStoragePath());
        videoRepository.delete(video);
    }

    public Resource loadStreamResource(Video video) {
        return storageService.loadAsResource(video.getStoragePath());
    }

    private VideoResponse toResponse(Video video) {
        return new VideoResponse(
                video.getId(),
                video.getTitle(),
                video.getOriginalFilename(),
                video.getContentType(),
                video.getSizeBytes(),
                video.getDurationSeconds(),
                video.getStatus().name(),
                video.getThumbnailPath() != null,
                video.getCreatedAt(),
                video.getUpdatedAt());
    }
}
