package io.navalis.api.application.dto.response;

import io.navalis.api.domain.model.GameStatus;

import java.util.UUID;

public record GameResponse(
        UUID gameId,
        String roomCode,
        GameStatus status,
        String message,
        String hostUsername
) {}
