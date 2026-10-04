ALTER TABLE orders
    ADD COLUMN delivery_status VARCHAR(40) NULL,
    ADD CONSTRAINT orders_delivery_status_valid
        CHECK (delivery_status IS NULL OR delivery_status IN (
            'Order Being Prepared',
            'Delivery On the Way',
            'Delivered'
        ));

UPDATE orders
SET delivery_status = 'Order Being Prepared'
WHERE fulfillment_method = 'Same-Day Delivery'
  AND delivery_status IS NULL;
