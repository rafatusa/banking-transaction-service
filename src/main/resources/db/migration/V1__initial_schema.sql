-- Banking Transaction Service — initial schema.
-- Owned by Flyway; Hibernate is configured with ddl-auto=validate and never mutates it.

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_role CHECK (role IN ('ADMIN', 'TELLER', 'CUSTOMER'))
);

CREATE TABLE account (
    id             BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(24)    NOT NULL UNIQUE,
    owner_username VARCHAR(64)    NOT NULL,
    -- Money is NUMERIC, never floating point.
    balance        NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    currency       CHAR(3)        NOT NULL DEFAULT 'USD',
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    version        BIGINT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    -- An account may never go negative: enforced at the database, not only in code.
    CONSTRAINT chk_account_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_account_owner ON account (owner_username);

CREATE TABLE transaction_record (
    id             BIGSERIAL PRIMARY KEY,
    reference      VARCHAR(36)    NOT NULL UNIQUE,
    source_account VARCHAR(24)    NOT NULL,
    target_account VARCHAR(24)    NOT NULL,
    amount         NUMERIC(19, 2) NOT NULL,
    currency       CHAR(3)        NOT NULL,
    status         VARCHAR(16)    NOT NULL,
    description    VARCHAR(255),
    initiated_by   VARCHAR(64)    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_txn_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_txn_status CHECK (status IN ('COMPLETED', 'REJECTED'))
);

CREATE INDEX idx_txn_source  ON transaction_record (source_account);
CREATE INDEX idx_txn_target  ON transaction_record (target_account);
CREATE INDEX idx_txn_created ON transaction_record (created_at DESC);

-- Append-only: the application exposes no UPDATE or DELETE path for this table.
CREATE TABLE audit_event (
    id            BIGSERIAL PRIMARY KEY,
    actor         VARCHAR(64) NOT NULL,
    action        VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_id   VARCHAR(64),
    outcome       VARCHAR(16) NOT NULL,
    detail        TEXT,
    source_ip     VARCHAR(45),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_actor   ON audit_event (actor);
CREATE INDEX idx_audit_created ON audit_event (created_at DESC);
