-- Apply once to databases created before password reset and order lifecycle support.
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE orders
    ALTER COLUMN order_status SET DEFAULT 'pending',
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP NULL;
