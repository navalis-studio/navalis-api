package io.navalis.api.interfaces.rest;

import io.navalis.api.application.dto.request.LoginRequest;
import io.navalis.api.application.dto.request.RegisterRequest;
import io.navalis.api.application.dto.response.AuthResponse;
import io.navalis.api.application.service.AuthService;
import io.navalis.api.infrastructure.config.MetricsConfig;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final MetricsConfig metrics;

    public AuthController(AuthService authService, MetricsConfig metrics) {
        this.authService = authService;
        this.metrics = metrics;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication) {
        UUID playerId = UUID.fromString(authentication.getName());
        metrics.playerLoggedOut(playerId);
        return ResponseEntity.noContent().build();
    }
}
