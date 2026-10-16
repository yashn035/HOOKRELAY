package com.hookrelay.api.controller;

import com.hookrelay.api.dto.AuthDtos.*;
import com.hookrelay.api.security.UserPrincipal;
import com.hookrelay.api.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "User registration, session tokens, and identity management")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @Operation(summary = "Register new account, organization, and default project")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest req) {
        AuthResponse response = authService.signup(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT tokens")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange refresh token for a fresh access token")
    public ResponseEntity<Map<String, String>> refresh(@Valid @RequestBody RefreshRequest req) {
        String token = authService.refreshToken(req.refreshToken());
        return ResponseEntity.ok(Map.of("token", token));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<UserSummary> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(authService.getCurrentUserSummary(principal.getId()));
    }
}
