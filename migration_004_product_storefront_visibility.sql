ALTER TABLE products
    ADD COLUMN IF NOT EXISTS is_visible BOOLEAN NOT NULL DEFAULT TRUE;

DROP VIEW IF EXISTS view_catalog_live_stock;

CREATE VIEW view_catalog_live_stock AS
SELECT
    p.id AS product_id,
    p.name,
    p.category,
    p.temperature_tier,
    p.unit,
    p.price_per_unit,
    p.image_url,
    p.reorder_level,
    COALESCE(SUM(b.remaining_qty), 0) AS in_stock_qty
FROM products p
LEFT JOIN inventory_batches b
    ON p.id = b.product_id
    AND b.is_deleted = FALSE
    AND b.remaining_qty > 0
    AND (b.expiration_date IS NULL OR b.expiration_date >= CURRENT_DATE)
WHERE p.is_deleted = FALSE
    AND p.is_visible = TRUE
GROUP BY p.id, p.name, p.category, p.temperature_tier, p.unit, p.price_per_unit, p.image_url, p.reorder_level;
