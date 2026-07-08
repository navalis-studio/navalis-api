package io.navalis.api.application.dto.request;

import io.navalis.api.domain.model.Orientation;
import io.navalis.api.domain.model.ShipType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceShipRequest(
        @NotNull(message = "Tipo do navio é obrigatório.")
        ShipType shipType,

        @Min(value = 0, message = "Linha deve ser entre 0 e 9.")
        @Max(value = 9, message = "Linha deve ser entre 0 e 9.")
        int row,

        @Min(value = 0, message = "Coluna deve ser entre 0 e 9.")
        @Max(value = 9, message = "Coluna deve ser entre 0 e 9.")
        int col,

        @NotNull(message = "Orientação é obrigatória.")
        Orientation orientation
) {}
