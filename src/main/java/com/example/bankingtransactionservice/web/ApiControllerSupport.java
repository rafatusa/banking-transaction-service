package com.example.bankingtransactionservice.web;

import com.example.bankingtransactionservice.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;

/**
 * Request-context helpers shared by the API controllers.
 *
 * <p>Extracted because every controller needs the caller's role and originating IP, and three
 * identical private copies is exactly the duplication CPD exists to catch. Keeping one
 * implementation also means the audit trail records the client address the same way everywhere.
 */
abstract class ApiControllerSupport {

    private static final String FORWARDED_HEADER = "X-Forwarded-For";
    private static final String ROLE_PREFIX = "ROLE_";

    /**
     * Resolves the caller's role from their granted authority.
     *
     * <p>Tokens carry exactly one role, so the first authority is the caller's role. Defaults to
     * the least privileged role if none is present.
     */
    protected Role currentRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(Object::toString)
                .map(authority -> authority.replace(ROLE_PREFIX, ""))
                .map(Role::valueOf)
                .orElse(Role.CUSTOMER);
    }

    /**
     * Resolves the originating client address.
     *
     * <p>nginx sits in front of the application, so the socket address is always the proxy. The
     * first entry of {@code X-Forwarded-For} is the real client; nginx sets that header, and the
     * application is configured with {@code forward-headers-strategy: framework}.
     */
    protected String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_HEADER);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
