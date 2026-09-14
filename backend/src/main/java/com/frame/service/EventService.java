package com.frame.service;

import com.frame.domain.entity.Event;
import com.frame.domain.entity.EventType;
import com.frame.dto.EventDtos.EventResponse;
import com.frame.exception.NotFoundException;
import com.frame.repository.EventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final VideoService videoService;

    public EventService(EventRepository eventRepository, VideoService videoService) {
        this.eventRepository = eventRepository;
        this.videoService = videoService;
    }

    /** Video ownership must already be verified by the caller (see VideoService#getOwned). */
    public List<EventResponse> search(UUID videoId, List<EventType> eventTypes, Long fromMs, Long toMs) {
        List<Event> events = (eventTypes == null || eventTypes.isEmpty())
                ? eventRepository.searchAllTypes(videoId, fromMs, toMs)
                : eventRepository.searchByTypes(videoId, eventTypes, fromMs, toMs);
        return events.stream().map(this::toResponse).toList();
    }

    /** Verifies the event's parent video is owned by {@code ownerId} before returning it. */
    public EventResponse get(UUID eventId, UUID ownerId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));
        videoService.getOwned(event.getVideoId(), ownerId);
        return toResponse(event);
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getId(),
                event.getVideoId(),
                event.getEventType().name(),
                event.getTimestampMs(),
                event.getEndTimestampMs(),
                event.getTitle(),
                event.getDescription(),
                event.getMetadata(),
                event.getConfidence(),
                event.getCreatedAt());
    }
}
