package com.storyteller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;
import java.util.UUID;

public record EventRequest(
        @NotNull(message = "tenantId is required") UUID tenantId,

        @NotNull(message = "userIdHash is required")
        @Pattern(regexp = "^[a-f0-9]{64}$", message = "userIdHash must be exactly 64 hexadecimal characters")
        String userIdHash,

        @NotNull(message = "eventType is required") String eventType,

        @NotNull(message = "videoId is required") UUID videoId,

        // Optional metadata for future extensibility (e.g. device_type, app_version, session_id)
        Map<String, Object> metadata) {
}
