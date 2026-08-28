package com.example.bankingtransactionservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for token issuing and verification. */
class JwtServiceTest {

    private static final String ISSUER = "banking-transaction-service";

    /**
     * Generates throwaway key material for a single test.
     *
     * <p>Generated rather than written as a literal so that no string resembling a signing key
     * ever appears in the source tree — a literal here would be indistinguishable, to a scanner or
     * to a reader, from a real leaked credential.
     */
    private static String randomKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private JwtService service(String key, long validityMinutes) {
        return new JwtService(key, validityMinutes, ISSUER);
    }

    @Test
    @DisplayName("round-trips subject and role through a signed token")
    void issuesAndParses() {
        JwtService jwt = service(randomKey(), 60);

        String token = jwt.issueToken("alice", "CUSTOMER");
        Optional<Claims> claims = jwt.parseToken(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().getSubject()).isEqualTo("alice");
        assertThat(claims.get().get("role", String.class)).isEqualTo("CUSTOMER");
        assertThat(claims.get().getIssuer()).isEqualTo(ISSUER);
        assertThat(jwt.getTokenValiditySeconds()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("refuses a token signed with a different key")
    void rejectsForeignSignature() {
        JwtService issuer = service(randomKey(), 60);
        JwtService verifier = service(randomKey(), 60);

        String token = issuer.issueToken("alice", "CUSTOMER");

        assertThat(verifier.parseToken(token)).isEmpty();
    }

    @Test
    @DisplayName("refuses an expired token")
    void rejectsExpiredToken() throws InterruptedException {
        // Zero-minute validity: the token expires the moment it is issued.
        JwtService jwt = service(randomKey(), 0);
        String token = jwt.issueToken("alice", "CUSTOMER");

        Thread.sleep(1100);

        assertThat(jwt.parseToken(token)).isEmpty();
    }

    @Test
    @DisplayName("refuses garbage input")
    void rejectsMalformedToken() {
        JwtService jwt = service(randomKey(), 60);

        assertThat(jwt.parseToken("not-a-jwt")).isEmpty();
        assertThat(jwt.parseToken("")).isEmpty();
    }

    @Test
    @DisplayName("refuses a token from a different issuer")
    void rejectsForeignIssuer() {
        String shared = randomKey();
        JwtService other = new JwtService(shared, 60, "some-other-service");
        JwtService jwt = service(shared, 60);

        String token = other.issueToken("alice", "CUSTOMER");

        assertThat(jwt.parseToken(token)).isEmpty();
    }

    @Test
    @DisplayName("refuses to start with a signing key that is too short for HS256")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtService("tooshort", 60, ISSUER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 characters");
    }
}
