package io.navalis.api.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FireRequest(
        @Min(value = 0, message = "Linha deve ser entre 0 e 9.")
        @Max(value = 9, message = "Linha deve ser entre 0 e 9.")
        int row,

        @Min(value = 0, message = "Coluna deve ser entre 0 e 9.")
        @Max(value = 9, message = "Coluna deve ser entre 0 e 9.")
        int col
) {}
