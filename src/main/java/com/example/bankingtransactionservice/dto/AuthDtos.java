package com.example.bankingtransactionservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and response payloads for authentication. */
public final class AuthDtos {

    private AuthDtos() {
        // Container for nested record types.
    }

    /** Credentials submitted to obtain a token. */
    public record LoginRequest(
            @NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) {}

    /** A successfully issued bearer token. */
    public record LoginResponse(String token, String tokenType, long expiresInSeconds, String role) {}
}
