CREATE SEQUENCE IF NOT EXISTS product_sku_sequence START WITH 1;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS sku VARCHAR(40);

UPDATE products
SET sku = 'SKU-' || LPAD(id::TEXT, 6, '0')
WHERE sku IS NULL;

CREATE TABLE IF NOT EXISTS product_sku_registry (
    sku VARCHAR(40) PRIMARY KEY,
    product_id INT NULL REFERENCES products(id) ON DELETE SET NULL,
    allocated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO product_sku_registry (sku, product_id)
SELECT sku, id
FROM products
WHERE sku IS NOT NULL
ON CONFLICT (sku) DO NOTHING;

SELECT setval(
    'product_sku_sequence',
    COALESCE((
        SELECT MAX(SUBSTRING(sku FROM 5)::BIGINT)
        FROM product_sku_registry
        WHERE sku ~ '^SKU-[0-9]+$'
    ), 0) + 1,
    FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS products_sku_unique ON products (sku);

ALTER TABLE products
    ALTER COLUMN sku SET NOT NULL;

CREATE TABLE IF NOT EXISTS product_aliases (
    id SERIAL PRIMARY KEY,
    product_id INT NULL REFERENCES products(id) ON DELETE SET NULL,
    alias VARCHAR(120) NOT NULL,
    retired_product_id INT NULL,
    retired_product_sku VARCHAR(40) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS product_aliases_alias_unique
    ON product_aliases (LOWER(alias));

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS product_sku_snapshot VARCHAR(40) NULL;

UPDATE order_items oi
SET product_sku_snapshot = p.sku
FROM products p
WHERE oi.product_id = p.id
  AND oi.product_sku_snapshot IS NULL;

ALTER TABLE inventory_batches
    ADD COLUMN IF NOT EXISTS product_sku_snapshot VARCHAR(40) NULL;

UPDATE inventory_batches b
SET product_sku_snapshot = p.sku
FROM products p
WHERE b.product_id = p.id
  AND b.product_sku_snapshot IS NULL;
