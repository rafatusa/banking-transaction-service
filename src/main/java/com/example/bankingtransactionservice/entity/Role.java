package com.example.bankingtransactionservice.entity;

/**
 * Role-based access control roles.
 *
 * <p>ADMIN sees everything including the audit trail; TELLER operates on any customer's accounts;
 * CUSTOMER is restricted to accounts they own.
 */
public enum Role {
    ADMIN,
    TELLER,
    CUSTOMER
}
