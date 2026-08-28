package com.example.bankingtransactionservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 *
 * <p>This filter is the single point where an anonymous HTTP request becomes an authenticated
 * principal, so each rejection path is asserted individually: a malformed header must leave the
 * context empty rather than half-populated, and the filter must ALWAYS continue the chain — a
 * filter that swallows a request turns a 401 into a hung connection.
 *
 * <p>Note the two-step stubbing in each test: the {@link Claims} mock is fully built on its own
 * line BEFORE it is handed to {@code when(jwtService.parseToken(...))}. Stubbing one mock inside
 * another {@code when(...)} argument list makes Mockito report {@code UnfinishedStubbing}.
 */
class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        filter = new JwtAuthenticationFilter(jwtService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static Claims claims(String subject, String role) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        when(claims.get("role", String.class)).thenReturn(role);
        return claims;
    }

    private static Authentication currentAuth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static List<String> currentAuthorities() {
        return currentAuth().getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("a valid bearer token populates the security context with the ROLE_ authority")
    void validTokenAuthenticates() throws Exception {
        Claims parsed = claims("alice", "CUSTOMER");
        request.addHeader("Authorization", "Bearer good-token");
        when(jwtService.parseToken("good-token")).thenReturn(Optional.of(parsed));

        filter.doFilter(request, response, chain);

        assertThat(currentAuth()).isNotNull();
        assertThat(currentAuth().getName()).isEqualTo("alice");
        assertThat(currentAuthorities()).containsExactly("ROLE_CUSTOMER");
        assertThat(currentAuth().getDetails()).isNotNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("an admin token yields the ROLE_ADMIN authority")
    void adminTokenAuthenticates() throws Exception {
        Claims parsed = claims("root", "ADMIN");
        request.addHeader("Authorization", "Bearer admin-token");
        when(jwtService.parseToken("admin-token")).thenReturn(Optional.of(parsed));

        filter.doFilter(request, response, chain);

        assertThat(currentAuthorities()).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("a request with no Authorization header passes through unauthenticated")
    void noHeaderLeavesContextEmpty() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(currentAuth()).isNull();
        verify(jwtService, never()).parseToken(anyString());
        verify(chain).doFilter(request, response);
    }

    @ParameterizedTest(name = "header \"{0}\" is ignored")
    @ValueSource(strings = {"Basic abc123", "bearer lowercase-prefix", "Bearer", "Token xyz", ""})
    @DisplayName("headers that are not a well-formed bearer token are ignored")
    void malformedHeadersAreIgnored(String headerValue) throws Exception {
        request.addHeader("Authorization", headerValue);

        filter.doFilter(request, response, chain);

        assertThat(currentAuth()).isNull();
        verify(jwtService, never()).parseToken(anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("a bearer header with an empty token is ignored")
    void emptyBearerTokenIsIgnored() throws Exception {
        request.addHeader("Authorization", "Bearer    ");

        filter.doFilter(request, response, chain);

        assertThat(currentAuth()).isNull();
        verify(jwtService, never()).parseToken(anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("an unparseable or expired token leaves the context empty")
    void invalidTokenLeavesContextEmpty() throws Exception {
        request.addHeader("Authorization", "Bearer expired");
        when(jwtService.parseToken("expired")).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertThat(currentAuth()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("a token without a subject is refused rather than authenticating an anonymous user")
    void tokenWithoutSubjectIsRefused() throws Exception {
        Claims parsed = claims(null, "ADMIN");
        request.addHeader("Authorization", "Bearer no-subject");
        when(jwtService.parseToken("no-subject")).thenReturn(Optional.of(parsed));

        filter.doFilter(request, response, chain);

        assertThat(currentAuth()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("a token without a role claim is refused rather than defaulting to a role")
    void tokenWithoutRoleIsRefused() throws Exception {
        Claims parsed = claims("alice", null);
        request.addHeader("Authorization", "Bearer no-role");
        when(jwtService.parseToken("no-role")).thenReturn(Optional.of(parsed));

        filter.doFilter(request, response, chain);

        assertThat(currentAuth()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("an already-authenticated context is never overwritten by a token")
    void existingAuthenticationIsPreserved() throws Exception {
        Authentication preset =
                new UsernamePasswordAuthenticationToken(
                        "preset-user", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(preset);

        request.addHeader("Authorization", "Bearer good-token");

        filter.doFilter(request, response, chain);

        assertThat(currentAuth()).isSameAs(preset);
        verify(jwtService, never()).parseToken(anyString());
        verify(chain).doFilter(request, response);
    }
}
