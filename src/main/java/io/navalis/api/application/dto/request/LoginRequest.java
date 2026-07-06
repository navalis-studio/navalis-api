package io.navalis.api.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Nome de usuário é obrigatório.")
        String username,

        @NotBlank(message = "Senha é obrigatória.")
        String password
) {}
