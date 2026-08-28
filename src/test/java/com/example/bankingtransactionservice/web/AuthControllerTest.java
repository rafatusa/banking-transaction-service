package com.example.bankingtransactionservice.web;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bankingtransactionservice.dto.AuthDtos;
import com.example.bankingtransactionservice.service.AuthService;
import com.example.bankingtransactionservice.service.BankingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice tests for {@link AuthController}.
 *
 * <p>{@code /api/auth/login} is one of the few endpoints {@code SecurityConfig} permits
 * anonymously, so these tests double as a check that the public-endpoint matcher still holds — a
 * regression there would lock every user out of the system.
 *
 * <p>Uses {@code @MockitoBean}, matching {@link WebMvcTestSupport}. Mixing it with the deprecated
 * {@code @MockBean} in one context activates BOTH Boot's legacy mock-bean post-processor and
 * Spring Framework's bean-override infrastructure, which killed the PIT minion JVM with
 * {@code UNKNOWN_ERROR} on the first Spring Boot 3.5 run.
 */
@WebMvcTest(AuthController.class)
class AuthControllerTest extends WebMvcTestSupport {

    private static final String LOGIN_BODY_TEMPLATE = "{\"username\":\"%s\",\"password\":\"%s\"}";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthService authService;

    private static String loginBody(String username, String password) {
        return String.format(LOGIN_BODY_TEMPLATE, username, password);
    }

    @Test
    @DisplayName("login is reachable without authentication and returns a token")
    void loginIsPublicAndIssuesToken() throws Exception {
        when(authService.login(eq("alice"), anyString(), anyString()))
                .thenReturn(new AuthDtos.LoginResponse("token-value", "Bearer", 3600L, "CUSTOMER"));

        mockMvc
                .perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody("alice", "correct-horse")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-value"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("bad credentials surface as a 403 problem detail, not a stack trace")
    void badCredentialsAreForbidden() throws Exception {
        when(authService.login(anyString(), anyString(), anyString()))
                .thenThrow(BankingException.forbidden("Invalid username or password"));

        mockMvc
                .perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody("alice", "wrong")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("a blank username never reaches the auth service")
    void blankUsernameRejected() throws Exception {
        mockMvc
                .perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody("", "something")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));

        verify(authService, never()).login(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a blank password never reaches the auth service")
    void blankPasswordRejected() throws Exception {
        mockMvc
                .perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody("alice", "")))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("the forwarded client address is passed to the auth service for the audit trail")
    void forwardedClientIpIsRecorded() throws Exception {
        when(authService.login(anyString(), anyString(), anyString()))
                .thenReturn(new AuthDtos.LoginResponse("t", "Bearer", 3600L, "ADMIN"));

        mockMvc
                .perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(loginBody("admin1", "secret-value"))
                                .header("X-Forwarded-For", "198.51.100.4, 10.0.0.1"))
                .andExpect(status().isOk());

        verify(authService).login(eq("admin1"), anyString(), eq("198.51.100.4"));
    }
}
