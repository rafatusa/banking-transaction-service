package com.example.bankingtransactionservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bankingtransactionservice.dto.AuthDtos;
import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.entity.UserAccount;
import com.example.bankingtransactionservice.repository.UserAccountRepository;
import com.example.bankingtransactionservice.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit tests for authentication. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String IP = "198.51.100.7";

    @Mock private UserAccountRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuditService auditService;

    @InjectMocks private AuthService authService;

    private UserAccount enabledUser() {
        return new UserAccount("alice", "hashed", Role.CUSTOMER);
    }

    @Test
    @DisplayName("issues a token for valid credentials")
    void issuesTokenOnSuccess() {
        UserAccount user = enabledUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hashed")).thenReturn(true);
        when(jwtService.issueToken("alice", "CUSTOMER")).thenReturn("token-value");
        when(jwtService.getTokenValiditySeconds()).thenReturn(3600L);

        AuthDtos.LoginResponse response = authService.login("alice", "secret", IP);

        assertThat(response.token()).isEqualTo("token-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(3600L);
        assertThat(response.role()).isEqualTo("CUSTOMER");
        verify(auditService)
                .record(eq("alice"), eq("LOGIN"), anyString(), anyString(), eq("SUCCESS"), anyString(), eq(IP));
    }

    @Test
    @DisplayName("rejects an unknown username without revealing that it is unknown")
    void rejectsUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", "secret", IP))
                .isInstanceOf(BankingException.class)
                .hasMessage("Invalid username or password");

        verify(auditService)
                .record(anyString(), eq("LOGIN"), anyString(), anyString(), eq("FAILURE"), anyString(), eq(IP));
    }

    @Test
    @DisplayName("rejects a wrong password with the same message as an unknown user")
    void rejectsWrongPassword() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(enabledUser()));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login("alice", "wrong", IP))
                .isInstanceOf(BankingException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    @DisplayName("rejects a disabled account")
    void rejectsDisabledUser() {
        UserAccount user = enabledUser();
        user.setEnabled(false);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login("alice", "secret", IP))
                .isInstanceOf(BankingException.class)
                .hasMessage("Invalid username or password");
    }

    @Test
    @DisplayName("looks up an existing user")
    void requiresUser() {
        UserAccount user = enabledUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThat(authService.requireUser("alice")).isSameAs(user);
    }

    @Test
    @DisplayName("raises NOT_FOUND for a missing user")
    void requireUserFailsWhenMissing() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.requireUser("ghost"))
                .isInstanceOf(BankingException.class)
                .hasMessageContaining("User not found");
    }
}
