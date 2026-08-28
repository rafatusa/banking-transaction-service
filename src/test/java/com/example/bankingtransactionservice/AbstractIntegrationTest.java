package com.example.bankingtransactionservice;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests.
 *
 * <p>Runs the application against a real PostgreSQL container so Flyway migrations, JPA mappings
 * and the database CHECK constraints are all exercised — an in-memory H2 would silently accept
 * schema the production database rejects.
 *
 * <p>The container is static: one instance is shared by every integration test class in the run,
 * which keeps the suite fast enough for CI. All credentials come from {@link TestCredentials},
 * which generates them at runtime.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("banking")
                    .withUsername(TestCredentials.DB_USER)
                    .withPassword(TestCredentials.DB_CREDENTIAL);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void applicationProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.jwt.secret", () -> TestCredentials.SIGNING_KEY);
        registry.add("app.seed.admin-username", () -> TestCredentials.ADMIN_USER);
        registry.add("app.seed.admin-password", () -> TestCredentials.ADMIN_CREDENTIAL);
    }
}
