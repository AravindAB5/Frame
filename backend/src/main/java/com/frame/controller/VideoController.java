package com.frame.controller;

import com.frame.domain.entity.EventType;
import com.frame.domain.entity.Video;
import com.frame.dto.EventDtos.EventResponse;
import com.frame.dto.MarkerDtos.MarkerCreateRequest;
import com.frame.dto.MarkerDtos.MarkerResponse;
import com.frame.dto.ProcessingJobDtos.ProcessingJobResponse;
import com.frame.dto.VideoDtos.VideoResponse;
import com.frame.security.FrameUserPrincipal;
import com.frame.service.EventService;
import com.frame.service.MarkerService;
import com.frame.service.ProcessingJobService;
import com.frame.service.VideoService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final VideoService videoService;
    private final EventService eventService;
    private final MarkerService markerService;
    private final ProcessingJobService processingJobService;

    public VideoController(
            VideoService videoService,
            EventService eventService,
            MarkerService markerService,
            ProcessingJobService processingJobService) {
        this.videoService = videoService;
        this.eventService = eventService;
        this.markerService = markerService;
        this.processingJobService = processingJobService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public VideoResponse upload(
            @AuthenticationPrincipal FrameUserPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "durationSeconds", required = false) Double durationSeconds)
            throws IOException {
        return videoService.upload(principal.id(), title, durationSeconds, file);
    }

    @GetMapping
    public List<VideoResponse> list(@AuthenticationPrincipal FrameUserPrincipal principal) {
        return videoService.listForOwner(principal.id());
    }

    @GetMapping("/{id}")
    public VideoResponse get(@AuthenticationPrincipal FrameUserPrincipal principal, @PathVariable UUID id) {
        return videoService.get(id, principal.id());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal FrameUserPrincipal principal, @PathVariable UUID id) {
        videoService.delete(id, principal.id());
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<ResourceRegion> stream(
            @AuthenticationPrincipal FrameUserPrincipal principal,
            @PathVariable UUID id,
            @RequestHeader HttpHeaders headers) {
        Video video = videoService.getOwned(id, principal.id());
        Resource resource = videoService.loadStreamResource(video);
        long contentLength = contentLengthOf(resource);

        List<HttpRange> ranges = headers.getRange();
        ResourceRegion region;
        HttpStatus status;
        if (ranges.isEmpty()) {
            region = new ResourceRegion(resource, 0, contentLength);
            status = HttpStatus.OK;
        } else {
            HttpRange range = ranges.get(0);
            long start = range.getRangeStart(contentLength);
            long end = range.getRangeEnd(contentLength);
            long rangeLength = Math.min(end - start + 1, contentLength - start);
            region = new ResourceRegion(resource, start, rangeLength);
            status = HttpStatus.PARTIAL_CONTENT;
        }

        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(video.getContentType()))
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    @GetMapping("/{id}/events")
    public List<EventResponse> events(
            @AuthenticationPrincipal FrameUserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(required = false) List<EventType> type,
            @RequestParam(required = false) Long fromMs,
            @RequestParam(required = false) Long toMs) {
        videoService.getOwned(id, principal.id());
        return eventService.search(id, type, fromMs, toMs);
    }

    @GetMapping("/{id}/markers")
    public List<MarkerResponse> markers(@AuthenticationPrincipal FrameUserPrincipal principal, @PathVariable UUID id) {
        return markerService.list(id, principal.id());
    }

    @PostMapping("/{id}/markers")
    @ResponseStatus(HttpStatus.CREATED)
    public MarkerResponse createMarker(
            @AuthenticationPrincipal FrameUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody MarkerCreateRequest request) {
        return markerService.create(id, principal.id(), request);
    }

    @GetMapping("/{id}/jobs")
    public List<ProcessingJobResponse> jobs(@AuthenticationPrincipal FrameUserPrincipal principal, @PathVariable UUID id) {
        videoService.getOwned(id, principal.id());
        return processingJobService.history(id);
    }

    @GetMapping("/{id}/jobs/latest")
    public ProcessingJobResponse latestJob(@AuthenticationPrincipal FrameUserPrincipal principal, @PathVariable UUID id) {
        videoService.getOwned(id, principal.id());
        return processingJobService.latest(id);
    }

    private long contentLengthOf(Resource resource) {
        try {
            return resource.contentLength();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
