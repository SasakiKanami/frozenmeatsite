ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS orders_payment_status_valid;

ALTER TABLE orders
    DROP CONSTRAINT IF EXISTS orders_payment_status_check;

ALTER TABLE orders
    ADD CONSTRAINT orders_payment_status_valid
        CHECK (payment_status IN ('unpaid', 'pending', 'paid', 'failed', 'cancelled'));

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS payment_reference VARCHAR(100),
    ADD COLUMN IF NOT EXISTS payment_deadline_at TIMESTAMP NULL;
