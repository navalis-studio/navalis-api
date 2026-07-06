package io.navalis.api.application.dto.response;

import java.util.UUID;

public record AuthResponse(
        UUID id,
        String username,
        String token
) {}
