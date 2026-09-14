package com.frame.controller;

import com.frame.dto.MarkerDtos.MarkerResponse;
import com.frame.dto.MarkerDtos.MarkerUpdateRequest;
import com.frame.security.FrameUserPrincipal;
import com.frame.service.MarkerService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/markers")
public class MarkerController {

    private final MarkerService markerService;

    public MarkerController(MarkerService markerService) {
        this.markerService = markerService;
    }

    @PatchMapping("/{id}")
    public MarkerResponse update(
            @AuthenticationPrincipal FrameUserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody MarkerUpdateRequest request) {
        return markerService.update(id, principal.id(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal FrameUserPrincipal principal, @PathVariable UUID id) {
        markerService.delete(id, principal.id());
    }
}
