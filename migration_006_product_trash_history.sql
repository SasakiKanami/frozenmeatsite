ALTER TABLE order_items
    ALTER COLUMN product_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS product_id_snapshot INT NULL,
    ADD COLUMN IF NOT EXISTS product_name_snapshot VARCHAR(120) NULL,
    ADD COLUMN IF NOT EXISTS product_unit_snapshot VARCHAR(20) NULL;

UPDATE order_items oi
SET product_id_snapshot = COALESCE(oi.product_id_snapshot, p.id),
    product_name_snapshot = COALESCE(oi.product_name_snapshot, p.name),
    product_unit_snapshot = COALESCE(oi.product_unit_snapshot, p.unit)
FROM products p
WHERE oi.product_id = p.id
  AND (oi.product_id_snapshot IS NULL
       OR oi.product_name_snapshot IS NULL
       OR oi.product_unit_snapshot IS NULL);

ALTER TABLE inventory_batches
    ALTER COLUMN product_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS product_id_snapshot INT NULL,
    ADD COLUMN IF NOT EXISTS product_name_snapshot VARCHAR(120) NULL;

UPDATE inventory_batches b
SET product_id_snapshot = COALESCE(b.product_id_snapshot, p.id),
    product_name_snapshot = COALESCE(b.product_name_snapshot, p.name)
FROM products p
WHERE b.product_id = p.id
  AND (b.product_id_snapshot IS NULL OR b.product_name_snapshot IS NULL);
