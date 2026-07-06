package io.navalis.api.application.service;

import io.navalis.api.application.dto.request.LoginRequest;
import io.navalis.api.application.dto.request.RegisterRequest;
import io.navalis.api.application.dto.response.AuthResponse;
import io.navalis.api.infrastructure.persistence.entity.UserEntity;
import io.navalis.api.infrastructure.persistence.repository.UserRepository;
import io.navalis.api.infrastructure.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("Nome de usuário já está em uso.");
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        UserEntity saved = userRepository.save(user);
        String token = jwtTokenProvider.generateToken(saved.getId(), saved.getUsername());

        return new AuthResponse(saved.getId(), saved.getUsername(), token);
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("Credenciais inválidas."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciais inválidas.");
        }

        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername());

        return new AuthResponse(user.getId(), user.getUsername(), token);
    }
}
