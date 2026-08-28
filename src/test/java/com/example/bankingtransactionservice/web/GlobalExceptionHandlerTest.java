package com.example.bankingtransactionservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.bankingtransactionservice.service.BankingException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Direct unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Tested directly rather than only through the controller slices because the mapping from
 * {@link BankingException.Kind} to HTTP status is an exhaustive switch: every arm needs exercising,
 * and driving all four through MockMvc would need four contrived endpoints. The catch-all handler
 * is also asserted to be OPAQUE — leaking an internal message there would be a real security
 * defect, not a cosmetic one.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    /**
     * A stable target for {@link MethodParameter}.
     *
     * <p>{@code MethodArgumentNotValidException} requires a real method parameter; binding it to a
     * dedicated dummy method keeps the tests independent of their own signatures.
     */
    @SuppressWarnings("unused")
    private void dummyEndpoint(String payload) {
        // Reflection target only.
    }

    private MethodParameter dummyParameter() throws NoSuchMethodException {
        return new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyEndpoint", String.class), 0);
    }

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/accounts/ACC-1");
        when(request.getMethod()).thenReturn("GET");
    }

    @ParameterizedTest(name = "{0} maps to HTTP {1}")
    @CsvSource({"NOT_FOUND, 404", "FORBIDDEN, 403", "CONFLICT, 409", "BUSINESS_RULE, 400"})
    @DisplayName("every domain failure kind maps to its HTTP status")
    void everyKindMapsToItsStatus(String kindName, int expectedStatus) {
        BankingException.Kind kind = BankingException.Kind.valueOf(kindName);
        BankingException ex = new BankingException(kind, "boom");

        ProblemDetail problem = handler.handleBanking(ex, request);

        assertThat(problem.getStatus()).isEqualTo(expectedStatus);
        assertThat(problem.getTitle()).isEqualTo(kindName);
        assertThat(problem.getDetail()).isEqualTo("boom");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/accounts/ACC-1"));
        assertThat(problem.getType().toString()).endsWith(kindName.toLowerCase(Locale.ROOT));
    }

    @Test
    @DisplayName("the problem type URI is stable and namespaced for every kind")
    void problemTypeIsNamespaced() {
        for (BankingException.Kind kind : BankingException.Kind.values()) {
            ProblemDetail problem = handler.handleBanking(new BankingException(kind, "x"), request);
            assertThat(problem.getType().toString())
                    .startsWith("https://banking-transaction-service/problems/");
        }
    }

    @Test
    @DisplayName("validation failures are reported field by field")
    void validationFailuresListEveryField() throws Exception {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "createAccountRequest");
        binding.addError(
                new FieldError("createAccountRequest", "currency", "must be a 3-letter ISO code"));
        binding.addError(
                new FieldError("createAccountRequest", "openingBalance", "cannot be negative"));

        ProblemDetail problem =
                handler.handleValidation(
                        new MethodArgumentNotValidException(dummyParameter(), binding), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("VALIDATION_FAILED");
        assertThat(problem.getDetail())
                .contains("currency: must be a 3-letter ISO code")
                .contains("openingBalance: cannot be negative");
    }

    @Test
    @DisplayName("a validation failure with no field errors still yields a usable message")
    void validationWithoutFieldErrorsHasFallbackMessage() throws Exception {
        BindingResult empty = new BeanPropertyBindingResult(new Object(), "createAccountRequest");

        ProblemDetail problem =
                handler.handleValidation(
                        new MethodArgumentNotValidException(dummyParameter(), empty), request);

        assertThat(problem.getDetail()).isEqualTo("Validation failed");
        assertThat(problem.getTitle()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a method-security denial becomes a 403 without echoing the internal message")
    void accessDeniedIsForbiddenAndGeneric() {
        ProblemDetail problem =
                handler.handleAccessDenied(
                        new AccessDeniedException("Access is denied to AccountController.close"), request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("FORBIDDEN");
        assertThat(problem.getDetail())
                .isEqualTo("You do not have permission to perform this action")
                .doesNotContain("AccountController");
    }

    @Test
    @DisplayName("the catch-all handler never leaks internal detail to the client")
    void unexpectedFailuresAreOpaque() {
        Exception internal =
                new IllegalStateException(
                        "relation \"account\" does not exist [jdbc:postgresql://db.internal:5432]");

        ProblemDetail problem = handler.handleUnexpected(internal, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("INTERNAL_ERROR");
        assertThat(problem.getDetail())
                .isEqualTo("An unexpected error occurred")
                .doesNotContain("jdbc:postgresql")
                .doesNotContain("relation");
    }
}
