package com.frame.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

public final class MarkerDtos {

    private MarkerDtos() {}

    public record MarkerCreateRequest(
            @Min(0) long timestampMs,
            @NotBlank String label,
            String note,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$") String color) {}

    public record MarkerUpdateRequest(Long timestampMs, String label, String note, String color) {}

    public record MarkerResponse(
            UUID id,
            UUID videoId,
            long timestampMs,
            String label,
            String note,
            String color,
            Instant createdAt,
            Instant updatedAt) {}
}
