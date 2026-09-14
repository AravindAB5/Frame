package com.frame.controller;

import com.frame.dto.EventDtos.EventResponse;
import com.frame.security.FrameUserPrincipal;
import com.frame.service.EventService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/{id}")
    public EventResponse get(@AuthenticationPrincipal FrameUserPrincipal principal, @PathVariable UUID id) {
        return eventService.get(id, principal.id());
    }
}
