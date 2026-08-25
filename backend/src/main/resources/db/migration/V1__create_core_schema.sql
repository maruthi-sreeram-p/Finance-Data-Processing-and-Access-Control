-- ---------------------------------------------------------------------------
-- V1: core schema -- users, categories, transactions.
--
-- The SQL here is deliberately restricted to the subset that behaves identically
-- on MySQL 8 and on H2 in MySQL compatibility mode, because the test suite runs
-- these same migrations against H2. That means: no ENUM columns (VARCHAR plus a
-- CHECK constraint instead), no MySQL-only functions, and no storage-engine or
-- charset clauses (InnoDB and utf8mb4 are already the MySQL 8 defaults).
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    -- BCrypt output is 60 characters; 100 leaves room to migrate to a longer
    -- algorithm (Argon2id encodes to ~95) without another schema change.
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_role   CHECK (role   IN ('ADMIN', 'MANAGER', 'USER')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
);

CREATE TABLE categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(60)  NOT NULL,
    description VARCHAR(255),
    type        VARCHAR(20)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT ck_categories_type CHECK (type IN ('INCOME', 'EXPENSE', 'BOTH'))
);

CREATE TABLE transactions (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    user_id          BIGINT        NOT NULL,
    category_id      BIGINT        NOT NULL,
    type             VARCHAR(20)   NOT NULL,
    -- DECIMAL, never DOUBLE: binary floating point cannot represent 0.10 exactly,
    -- so a few thousand summed expenses would drift off the true total.
    amount           DECIMAL(19, 2) NOT NULL,
    description      VARCHAR(255),
    transaction_date DATE          NOT NULL,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_transactions_category
        FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_transactions_type CHECK (type IN ('INCOME', 'EXPENSE')),
    -- Direction lives in `type`, so a negative or zero amount is always a bug.
    -- Enforced here as well as in Bean Validation: the database is the last line
    -- of defence against a bad write that bypasses the API layer.
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0)
);

-- Every user-facing query filters by user_id first; the trailing column matches
-- the default sort and the date-range filter so both are served by one index.
CREATE INDEX idx_transactions_user_date ON transactions (user_id, transaction_date);
CREATE INDEX idx_transactions_user_type ON transactions (user_id, type);
CREATE INDEX idx_transactions_category  ON transactions (category_id);
