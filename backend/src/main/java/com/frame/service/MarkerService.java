package com.frame.service;

import com.frame.domain.entity.Marker;
import com.frame.dto.MarkerDtos.MarkerCreateRequest;
import com.frame.dto.MarkerDtos.MarkerResponse;
import com.frame.dto.MarkerDtos.MarkerUpdateRequest;
import com.frame.exception.NotFoundException;
import com.frame.repository.MarkerRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MarkerService {

    private final MarkerRepository markerRepository;
    private final VideoService videoService;

    public MarkerService(MarkerRepository markerRepository, VideoService videoService) {
        this.markerRepository = markerRepository;
        this.videoService = videoService;
    }

    public List<MarkerResponse> list(UUID videoId, UUID ownerId) {
        videoService.getOwned(videoId, ownerId);
        return markerRepository.findByVideoIdOrderByTimestampMsAsc(videoId).stream().map(this::toResponse).toList();
    }

    public MarkerResponse create(UUID videoId, UUID ownerId, MarkerCreateRequest request) {
        videoService.getOwned(videoId, ownerId);
        Marker marker = Marker.builder()
                .videoId(videoId)
                .userId(ownerId)
                .timestampMs(request.timestampMs())
                .label(request.label())
                .note(request.note())
                .color(StringUtils.hasText(request.color()) ? request.color() : "#38bdf8")
                .build();
        return toResponse(markerRepository.save(marker));
    }

    public MarkerResponse update(UUID markerId, UUID ownerId, MarkerUpdateRequest request) {
        Marker marker = getOwned(markerId, ownerId);
        if (request.timestampMs() != null) {
            marker.setTimestampMs(request.timestampMs());
        }
        if (StringUtils.hasText(request.label())) {
            marker.setLabel(request.label());
        }
        if (request.note() != null) {
            marker.setNote(request.note());
        }
        if (StringUtils.hasText(request.color())) {
            marker.setColor(request.color());
        }
        return toResponse(markerRepository.save(marker));
    }

    public void delete(UUID markerId, UUID ownerId) {
        Marker marker = getOwned(markerId, ownerId);
        markerRepository.delete(marker);
    }

    private Marker getOwned(UUID markerId, UUID ownerId) {
        Marker marker = markerRepository.findById(markerId).orElseThrow(() -> new NotFoundException("Marker not found"));
        if (!marker.getUserId().equals(ownerId)) {
            throw new NotFoundException("Marker not found");
        }
        return marker;
    }

    private MarkerResponse toResponse(Marker marker) {
        return new MarkerResponse(
                marker.getId(),
                marker.getVideoId(),
                marker.getTimestampMs(),
                marker.getLabel(),
                marker.getNote(),
                marker.getColor(),
                marker.getCreatedAt(),
                marker.getUpdatedAt());
    }
}
