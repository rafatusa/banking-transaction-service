package com.example.bankingtransactionservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Issues and validates the HMAC-signed JWTs used to authenticate API callers. */
@Service
public class JwtService {

    private static final String CLAIM_ROLE = "role";

    private final SecretKey signingKey;
    private final Duration tokenValidity;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.validity-minutes:60}") long validityMinutes,
            @Value("${app.jwt.issuer:banking-transaction-service}") String issuer) {
        // HS256 requires >= 256 bits of key material; the deploy pipeline generates a
        // 48-char alphanumeric secret, and application.yml has no default for it.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 characters to sign HS256 tokens");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.tokenValidity = Duration.ofMinutes(validityMinutes);
        this.issuer = issuer;
    }

    /** Issues a signed token carrying the subject's username and role. */
    public String issueToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_ROLE, role)
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(tokenValidity)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and verifies a token.
     *
     * @return the token's claims, or empty when the token is malformed, expired, or not signed by
     *     this service
     */
    public Optional<Claims> parseToken(String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(signingKey)
                            .requireIssuer(issuer)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            // Invalid tokens are an expected condition, not an error worth a stack trace.
            return Optional.empty();
        }
    }

    /** Returns the validity window applied to newly issued tokens, in seconds. */
    public long getTokenValiditySeconds() {
        return tokenValidity.toSeconds();
    }
}
