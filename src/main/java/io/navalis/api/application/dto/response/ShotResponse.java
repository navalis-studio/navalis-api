package io.navalis.api.application.dto.response;

import io.navalis.api.domain.model.ShipType;
import io.navalis.api.domain.model.ShotResult;

import java.util.UUID;

public record ShotResponse(
        ShotResult result,
        ShipType sunkShipType,
        boolean gameOver,
        UUID winnerId
) {}
