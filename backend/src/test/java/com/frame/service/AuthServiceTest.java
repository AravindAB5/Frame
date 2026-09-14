package com.frame.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.frame.domain.entity.User;
import com.frame.dto.AuthDtos.AuthResponse;
import com.frame.dto.AuthDtos.LoginRequest;
import com.frame.dto.AuthDtos.RegisterRequest;
import com.frame.exception.ConflictException;
import com.frame.repository.UserRepository;
import com.frame.security.JwtService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtService jwtService = new JwtService("dev-only-secret-key-change-me-please-32bytes-min", 60);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerRejectsAnAlreadyUsedEmail() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest("taken@example.com", "password123", "Taken Person");

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(ConflictException.class);
    }

    @Test
    void registerHashesThePasswordBeforeSaving() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });

        RegisterRequest request = new RegisterRequest("new@example.com", "password123", "New Person");
        AuthResponse response = authService.register(request);

        assertThat(response.token()).isNotBlank();
        assertThat(response.user().email()).isEqualTo("new@example.com");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void loginRejectsAWrongPassword() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("person@example.com")
                .passwordHash(passwordEncoder.encode("correct-password"))
                .displayName("Person")
                .build();
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("person@example.com", "wrong-password");

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void loginSucceedsWithCorrectCredentialsAndReturnsAValidToken() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("person@example.com")
                .passwordHash(passwordEncoder.encode("correct-password"))
                .displayName("Person")
                .build();
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));

        AuthResponse response = authService.login(new LoginRequest("person@example.com", "correct-password"));

        assertThat(jwtService.parse(response.token()).id()).isEqualTo(user.getId());
    }
}
