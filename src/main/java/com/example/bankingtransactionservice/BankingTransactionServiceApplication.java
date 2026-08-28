package com.example.bankingtransactionservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Application entry point. */
@SpringBootApplication
public class BankingTransactionServiceApplication {

    protected BankingTransactionServiceApplication() {
        // Spring Boot entry point: instantiated by the framework, not by callers.
    }

    /**
     * Starts the service.
     *
     * @param args standard JVM command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(BankingTransactionServiceApplication.class, args);
    }
}
