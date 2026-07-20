package io.navalis.api.application.service;

import io.navalis.api.application.dto.request.LoginRequest;
import io.navalis.api.application.dto.request.RegisterRequest;
import io.navalis.api.application.dto.response.AuthResponse;
import io.navalis.api.infrastructure.persistence.entity.UserEntity;
import io.navalis.api.infrastructure.persistence.repository.UserRepository;
import io.navalis.api.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    @Nested
    class Register {

        @Test
        void shouldRegisterNewUserSuccessfully() {
            RegisterRequest request = new RegisterRequest("navalis_player", "secret123");
            UUID generatedId = UUID.randomUUID();

            when(userRepository.existsByUsername("navalis_player")).thenReturn(false);
            when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$hashed");
            when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
                UserEntity entity = invocation.getArgument(0);
                entity.setId(generatedId);
                return entity;
            });
            when(jwtTokenProvider.generateToken(generatedId, "navalis_player")).thenReturn("jwt-token");

            AuthResponse response = authService.register(request);

            assertEquals(generatedId, response.id());
            assertEquals("navalis_player", response.username());
            assertEquals("jwt-token", response.token());
        }

        @Test
        void shouldHashPasswordBeforeSaving() {
            RegisterRequest request = new RegisterRequest("player", "mypassword");

            when(userRepository.existsByUsername("player")).thenReturn(false);
            when(passwordEncoder.encode("mypassword")).thenReturn("$2a$10$encoded");
            when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> {
                UserEntity entity = invocation.getArgument(0);
                entity.setId(UUID.randomUUID());
                return entity;
            });
            when(jwtTokenProvider.generateToken(any(), eq("player"))).thenReturn("token");

            authService.register(request);

            ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(captor.capture());
            assertEquals("$2a$10$encoded", captor.getValue().getPasswordHash());
        }

        @Test
        void shouldRejectDuplicateUsername() {
            RegisterRequest request = new RegisterRequest("existing_user", "password");

            when(userRepository.existsByUsername("existing_user")).thenReturn(true);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> authService.register(request));

            assertEquals("Nome de usuário já está em uso.", ex.getMessage());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    class Login {

        @Test
        void shouldLoginWithValidCredentials() {
            LoginRequest request = new LoginRequest("player", "correct_password");
            UUID userId = UUID.randomUUID();

            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setUsername("player");
            user.setPasswordHash("$2a$10$hashed");

            when(userRepository.findByUsername("player")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("correct_password", "$2a$10$hashed")).thenReturn(true);
            when(jwtTokenProvider.generateToken(userId, "player")).thenReturn("jwt-token");

            AuthResponse response = authService.login(request);

            assertEquals(userId, response.id());
            assertEquals("player", response.username());
            assertEquals("jwt-token", response.token());
        }

        @Test
        void shouldRejectLoginWithWrongPassword() {
            LoginRequest request = new LoginRequest("player", "wrong_password");

            UserEntity user = new UserEntity();
            user.setId(UUID.randomUUID());
            user.setUsername("player");
            user.setPasswordHash("$2a$10$hashed");

            when(userRepository.findByUsername("player")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong_password", "$2a$10$hashed")).thenReturn(false);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> authService.login(request));

            assertEquals("Credenciais inválidas.", ex.getMessage());
        }

        @Test
        void shouldRejectLoginWithNonExistentUser() {
            LoginRequest request = new LoginRequest("ghost", "password");

            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> authService.login(request));

            assertEquals("Credenciais inválidas.", ex.getMessage());
        }

        @Test
        void shouldNotRevealWhetherUserExistsOnFailedLogin() {
            // Both "user not found" and "wrong password" should return same message
            LoginRequest requestBadUser = new LoginRequest("nonexistent", "pass");
            LoginRequest requestBadPass = new LoginRequest("existing", "wrongpass");

            when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            UserEntity user = new UserEntity();
            user.setId(UUID.randomUUID());
            user.setUsername("existing");
            user.setPasswordHash("hash");
            when(userRepository.findByUsername("existing")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongpass", "hash")).thenReturn(false);

            IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                    () -> authService.login(requestBadUser));
            IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                    () -> authService.login(requestBadPass));

            assertEquals(ex1.getMessage(), ex2.getMessage());
        }
    }
}
