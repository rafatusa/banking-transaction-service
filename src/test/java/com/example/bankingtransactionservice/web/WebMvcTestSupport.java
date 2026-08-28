package com.example.bankingtransactionservice.web;

import com.example.bankingtransactionservice.config.SecurityConfig;
import com.example.bankingtransactionservice.security.JwtAuthenticationFilter;
import com.example.bankingtransactionservice.security.JwtService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

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
 */
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
abstract class WebMvcTestSupport {

    @MockBean protected JwtService jwtService;
}
