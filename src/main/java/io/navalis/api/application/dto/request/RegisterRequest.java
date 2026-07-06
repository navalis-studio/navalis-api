package io.navalis.api.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Nome de usuário é obrigatório.")
        @Size(min = 3, max = 50, message = "Nome de usuário deve ter entre 3 e 50 caracteres.")
        String username,

        @NotBlank(message = "Senha é obrigatória.")
        @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres.")
        String password
) {}
