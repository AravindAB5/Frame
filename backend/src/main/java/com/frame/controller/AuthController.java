package com.frame.controller;

import com.frame.dto.AuthDtos.AuthResponse;
import com.frame.dto.AuthDtos.LoginRequest;
import com.frame.dto.AuthDtos.RegisterRequest;
import com.frame.dto.AuthDtos.UserResponse;
import com.frame.security.FrameUserPrincipal;
import com.frame.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal FrameUserPrincipal principal) {
        return authService.me(principal.id());
    }
}
