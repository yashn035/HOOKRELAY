package com.hookrelay.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class AuthDtos {

    public record SignupRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Password is required")
            @Size(min = 8, message = "Password must be at least 8 characters")
            String password,

            @NotBlank(message = "Full name is required")
            String fullName,

            @NotBlank(message = "Organization name is required")
            String orgName
    ) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Invalid email format")
            String email,

            @NotBlank(message = "Password is required")
            String password
    ) {}

    public record RefreshRequest(
            @NotBlank(message = "Refresh token is required")
            String refreshToken
    ) {}

    public record UserSummary(
            UUID id,
            String email,
            String fullName,
            String role,
            UUID orgId,
            String orgName
    ) {}

    public record AuthResponse(
            String token,
            String refreshToken,
            UserSummary user,
            UUID defaultProjectId
    ) {}
}
