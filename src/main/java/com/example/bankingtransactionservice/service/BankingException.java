package com.example.bankingtransactionservice.service;

/** Domain-level failures that map onto deterministic HTTP responses. */
public class BankingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** The category of failure, used by the exception handler to pick a status code. */
    public enum Kind {
        /** The referenced resource does not exist. */
        NOT_FOUND,
        /** The request is well-formed but violates a business rule. */
        BUSINESS_RULE,
        /** The caller may not act on this resource. */
        FORBIDDEN,
        /** The request conflicts with the current state of the resource. */
        CONFLICT
    }

    private final transient Kind kind;

    public BankingException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    /** Convenience factory for a missing resource. */
    public static BankingException notFound(String message) {
        return new BankingException(Kind.NOT_FOUND, message);
    }

    /** Convenience factory for a violated business rule. */
    public static BankingException businessRule(String message) {
        return new BankingException(Kind.BUSINESS_RULE, message);
    }

    /** Convenience factory for an authorization failure. */
    public static BankingException forbidden(String message) {
        return new BankingException(Kind.FORBIDDEN, message);
    }

    /** Convenience factory for a state conflict. */
    public static BankingException conflict(String message) {
        return new BankingException(Kind.CONFLICT, message);
    }
}
