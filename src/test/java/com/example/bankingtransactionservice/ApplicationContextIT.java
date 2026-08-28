package com.example.bankingtransactionservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bankingtransactionservice.service.AccountService;
import com.example.bankingtransactionservice.service.AuditService;
import com.example.bankingtransactionservice.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifies the Spring context starts and the core beans are wired.
 *
 * <p>Named {@code *IT} deliberately: it starts a real PostgreSQL container, so it belongs to
 * Failsafe and the integration-test stage. Naming it {@code *Test} would make Surefire run it in
 * the unit-test stage, where Docker is not guaranteed to be available.
 */
class ApplicationContextIT extends AbstractIntegrationTest {

    @Autowired private AccountService accountService;
    @Autowired private TransferService transferService;
    @Autowired private AuditService auditService;

    @Test
    @DisplayName("application context loads with all core services")
    void contextLoads() {
        assertThat(accountService).isNotNull();
        assertThat(transferService).isNotNull();
        assertThat(auditService).isNotNull();
    }
}
