package io.navalis.api.application.dto.response;

import io.navalis.api.domain.model.GameStatus;

import java.util.UUID;

public record GameResponse(
        UUID gameId,
        GameStatus status,
        String message
) {}
