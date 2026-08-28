package com.example.bankingtransactionservice.service;

import com.example.bankingtransactionservice.dto.AuthDtos;
import com.example.bankingtransactionservice.entity.UserAccount;
import com.example.bankingtransactionservice.repository.UserAccountRepository;
import com.example.bankingtransactionservice.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authenticates users and issues tokens. */
@Service
public class AuthService {

    private final UserAccountRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    /**
     * Verifies credentials and issues a bearer token.
     *
     * <p>Failures are deliberately indistinguishable to the caller — unknown username, wrong
     * password and disabled account all produce the same message, so the endpoint cannot be used to
     * enumerate valid usernames.
     */
    @Transactional(readOnly = true)
    public AuthDtos.LoginResponse login(String username, String password, String sourceIp) {
        UserAccount user = userRepository.findByUsername(username).orElse(null);

        if (user == null || !user.isEnabled() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            auditService.record(
                    username, "LOGIN", "UserAccount", username, "FAILURE", "Invalid credentials", sourceIp);
            throw BankingException.forbidden("Invalid username or password");
        }

        String token = jwtService.issueToken(user.getUsername(), user.getRole().name());
        auditService.record(
                user.getUsername(),
                "LOGIN",
                "UserAccount",
                user.getUsername(),
                "SUCCESS",
                "Token issued",
                sourceIp);

        return new AuthDtos.LoginResponse(
                token, "Bearer", jwtService.getTokenValiditySeconds(), user.getRole().name());
    }

    /** Looks up a user by username. */
    @Transactional(readOnly = true)
    public UserAccount requireUser(String username) {
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> BankingException.notFound("User not found: " + username));
    }
}
