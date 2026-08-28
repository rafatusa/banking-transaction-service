package com.example.bankingtransactionservice.web;

import com.example.bankingtransactionservice.config.SecurityConfig;
import com.example.bankingtransactionservice.security.JwtAuthenticationFilter;
import com.example.bankingtransactionservice.security.JwtService;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Shared wiring for the {@code @WebMvcTest} controller slices.
 *
 * <p>The production {@link SecurityConfig} is imported so these tests exercise the REAL
 * authorization rules — {@code @PreAuthorize} RBAC, the 401 entry point, the public-endpoint
 * matchers. Substituting a permissive stand-in security config would prove nothing about who may
 * call what, which is the part that matters most in a banking API.
 *
 * <p>{@link JwtAuthenticationFilter} is imported as the REAL bean rather than mocked: it extends
 * {@code OncePerRequestFilter}, and a Mockito mock would never invoke {@code
 * filterChain.doFilter(...)}, silently swallowing every request. Only its {@link JwtService}
 * collaborator is mocked — it returns no claims by default, so the filter is an inert pass-through
 * and the authenticated principal comes from {@code @WithMockUser} or a request post-processor.
 * Token parsing itself is covered by {@code JwtServiceTest}.
 *
 * <p>{@code @MockitoBean} replaces the deprecated {@code @MockBean}: Spring Boot 3.4 moved
 * test-bean overriding into Spring Framework's {@code bean.override.mockito} package, and
 * {@code @MockBean} is removed in Spring Boot 4. The semantics are identical.
 */
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
abstract class WebMvcTestSupport {

    @MockitoBean protected JwtService jwtService;
}
