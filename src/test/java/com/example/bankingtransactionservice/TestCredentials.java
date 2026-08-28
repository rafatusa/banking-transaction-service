package com.example.bankingtransactionservice;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Throwaway credentials for the integration test suite.
 *
 * <p>Values are GENERATED at class-load time rather than written as literals. Two reasons: a
 * credential-shaped literal in the source tree is indistinguishable from a real leaked one to both
 * scanners and readers, and generated values make it impossible for a test credential to be copied
 * into a deployed environment by accident.
 *
 * <p>Every value is stable for the lifetime of a single JVM, so the container, the application
 * context and the tests all agree within one run.
 */
public final class TestCredentials {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Database user for the Testcontainers PostgreSQL instance. */
    public static final String DB_USER = "banking_test";

    /** Database credential for the Testcontainers PostgreSQL instance. */
    public static final String DB_CREDENTIAL = generate();

    /** HS256 signing key for the test application context. */
    public static final String SIGNING_KEY = generate();

    /** Bootstrap administrator username seeded into the test database. */
    public static final String ADMIN_USER = "itadmin";

    /** Bootstrap administrator credential. */
    public static final String ADMIN_CREDENTIAL = generate();

    /** A non-privileged user seeded by the API tests. */
    public static final String CUSTOMER_USER = "itcustomer";

    /** Credential for the non-privileged user. */
    public static final String CUSTOMER_CREDENTIAL = generate();

    private TestCredentials() {
        // Constants only.
    }

    /** Produces 64 hex characters — comfortably above the 32-character HS256 minimum. */
    private static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
