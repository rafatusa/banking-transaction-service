package com.example.bankingtransactionservice.config;

import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.entity.UserAccount;
import com.example.bankingtransactionservice.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the initial operator account.
 *
 * <p>The credentials come from the environment, never from a migration file: a BCrypt hash
 * committed to SQL is a hardcoded credential in the repository, which the secret scanners would
 * (correctly) flag. Seeding is idempotent — an existing user is left untouched, so redeploys and
 * recovery reruns are safe.
 */
@Configuration
public class DataSeeder {

    private static final Logger LOG = LoggerFactory.getLogger(DataSeeder.class);

    /** Creates the bootstrap admin user if it does not already exist. */
    @Bean
    public ApplicationRunner seedUsers(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.seed.admin-username:}") String adminUsername,
            @Value("${app.seed.admin-password:}") String adminPassword) {

        return args -> seed(userRepository, passwordEncoder, adminUsername, adminPassword);
    }

    @Transactional
    void seed(
            UserAccountRepository userRepository,
            PasswordEncoder passwordEncoder,
            String adminUsername,
            String adminPassword) {

        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            LOG.info("No seed credentials supplied; skipping bootstrap user creation");
            return;
        }

        if (userRepository.existsByUsername(adminUsername)) {
            LOG.info("Bootstrap user already present; nothing to seed");
            return;
        }

        userRepository.save(
                new UserAccount(adminUsername, passwordEncoder.encode(adminPassword), Role.ADMIN));
        LOG.info("Created bootstrap ADMIN user");
    }
}
