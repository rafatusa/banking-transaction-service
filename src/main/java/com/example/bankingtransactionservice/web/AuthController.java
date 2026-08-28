package com.example.bankingtransactionservice.web;

import com.example.bankingtransactionservice.dto.AuthDtos;
import com.example.bankingtransactionservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Authentication endpoints. */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Obtain a bearer token for the API")
public class AuthController extends ApiControllerSupport {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Exchanges username and password for a signed JWT. */
    @PostMapping("/login")
    @Operation(
            summary = "Log in",
            description =
                    "Verifies credentials and returns a bearer token. Send the token as "
                            + "'Authorization: Bearer <token>' on every subsequent request.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token issued"),
        @ApiResponse(responseCode = "400", description = "Malformed request"),
        @ApiResponse(responseCode = "403", description = "Invalid username or password")
    })
    public ResponseEntity<AuthDtos.LoginResponse> login(
            @Valid @RequestBody AuthDtos.LoginRequest request, HttpServletRequest httpRequest) {
        AuthDtos.LoginResponse response =
                authService.login(request.username(), request.password(), clientIp(httpRequest));
        return ResponseEntity.ok(response);
    }
}
