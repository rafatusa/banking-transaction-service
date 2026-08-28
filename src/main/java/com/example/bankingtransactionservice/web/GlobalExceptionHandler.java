package com.example.bankingtransactionservice.web;

import com.example.bankingtransactionservice.service.BankingException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into RFC 7807 problem responses.
 *
 * <p>Messages are deliberately free of internal detail — stack traces and SQL state never reach the
 * client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_TYPE = "https://banking-transaction-service/problems/";

    /** Maps domain failures onto their HTTP status. */
    @ExceptionHandler(BankingException.class)
    public ProblemDetail handleBanking(BankingException ex, HttpServletRequest request) {
        HttpStatus status =
                switch (ex.getKind()) {
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                    case FORBIDDEN -> HttpStatus.FORBIDDEN;
                    case CONFLICT -> HttpStatus.CONFLICT;
                    case BUSINESS_RULE -> HttpStatus.BAD_REQUEST;
                };

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle(ex.getKind().name());
        problem.setType(URI.create(BASE_TYPE + ex.getKind().name().toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    /** Reports bean-validation failures field by field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(error -> error.getField() + ": " + error.getDefaultMessage())
                        .collect(Collectors.joining("; "));

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, detail.isEmpty() ? "Validation failed" : detail);
        problem.setTitle("VALIDATION_FAILED");
        problem.setType(URI.create(BASE_TYPE + "validation"));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    /** Maps Spring Security's method-level denials onto 403. */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
        problem.setTitle("FORBIDDEN");
        problem.setType(URI.create(BASE_TYPE + "forbidden"));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }

    /** Catch-all: logged in full server-side, opaque to the client. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        LOG.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("INTERNAL_ERROR");
        problem.setType(URI.create(BASE_TYPE + "internal"));
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
