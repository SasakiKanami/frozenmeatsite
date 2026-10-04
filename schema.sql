-- Carni-Flow Relational Database Schema
-- Database: frozenmeatsite_db

DROP VIEW IF EXISTS view_catalog_live_stock CASCADE;
DROP TABLE IF EXISTS archived_batches CASCADE;
DROP TABLE IF EXISTS archived_orders CASCADE;
DROP TABLE IF EXISTS password_reset_tokens CASCADE;
DROP TABLE IF EXISTS batch_deductions CASCADE;
DROP TABLE IF EXISTS order_items CASCADE;
DROP TABLE IF EXISTS product_aliases CASCADE;
DROP TABLE IF EXISTS product_sku_registry CASCADE;
DROP TABLE IF EXISTS orders CASCADE;
DROP TABLE IF EXISTS inventory_batches CASCADE;
DROP TABLE IF EXISTS products CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP SEQUENCE IF EXISTS product_sku_sequence CASCADE;

-- 1. Users Table (Role-based access & hashed passwords)
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('admin', 'cashier', 'customer')),
    email VARCHAR(255),
    phone VARCHAR(30),
    address_line TEXT,
    city VARCHAR(100),
    landmark TEXT,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

CREATE TABLE password_reset_tokens (
    id SERIAL PRIMARY KEY,
    user_id INT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Products Table (Includes image_url for admin uploads)
CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    sku VARCHAR(40) UNIQUE NOT NULL,
    name VARCHAR(120) UNIQUE NOT NULL,
    category VARCHAR(50) NOT NULL, 
    temperature_tier VARCHAR(30) NOT NULL CHECK (temperature_tier IN ('Fresh Chilled', 'Deep Freeze', 'Processed Pack')),
    unit VARCHAR(20) NOT NULL CHECK (unit IN ('kg', 'pack')),
    price_per_unit NUMERIC(10, 2) NOT NULL CHECK (price_per_unit >= 0),
    image_url TEXT NULL,
    reorder_level NUMERIC(10, 2) DEFAULT 5.00,
    is_visible BOOLEAN NOT NULL DEFAULT TRUE,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

-- 3. Inventory Batches Table (Arrival tracking for strict FIFO deduction)
CREATE TABLE inventory_batches (
    id SERIAL PRIMARY KEY,
    product_id INT NULL REFERENCES products(id) ON DELETE RESTRICT,
    product_id_snapshot INT NULL,
    product_name_snapshot VARCHAR(120) NULL,
    product_sku_snapshot VARCHAR(40) NULL,
    batch_number VARCHAR(50) NOT NULL,
    supplier_name VARCHAR(100) DEFAULT 'Direct Meat Supplier',
    initial_qty NUMERIC(10, 2) NOT NULL CHECK (initial_qty > 0),
    remaining_qty NUMERIC(10, 2) NOT NULL CHECK (remaining_qty >= 0),
    arrival_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expiration_date DATE NULL,
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

CREATE TABLE product_sku_registry (
    sku VARCHAR(40) PRIMARY KEY,
    product_id INT NULL REFERENCES products(id) ON DELETE SET NULL,
    allocated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE product_sku_sequence START WITH 1;

CREATE TABLE product_aliases (
    id SERIAL PRIMARY KEY,
    product_id INT NULL REFERENCES products(id) ON DELETE SET NULL,
    alias VARCHAR(120) NOT NULL,
    retired_product_id INT NULL,
    retired_product_sku VARCHAR(40) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX product_aliases_alias_unique ON product_aliases (LOWER(alias));

-- 4. Orders Table (Matches guest forms and POS checkouts)
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    reference_id VARCHAR(50) UNIQUE NOT NULL,
    order_source VARCHAR(20) NOT NULL CHECK (order_source IN ('walk_in', 'online')),
    customer_name VARCHAR(100) NOT NULL,
    customer_contact VARCHAR(30) NOT NULL,
    customer_email VARCHAR(255),
    customer_user_id INT NULL REFERENCES users(id),
    fulfillment_method VARCHAR(50) NOT NULL CHECK (fulfillment_method IN ('Storefront Pickup', 'Same-Day Delivery')),
    delivery_address TEXT,
    delivery_notes TEXT,
    delivery_fee NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (delivery_fee >= 0),
    delivery_status VARCHAR(40) NULL CHECK (delivery_status IS NULL OR delivery_status IN ('Order Being Prepared', 'Delivery On the Way', 'Delivered')),
    payment_method VARCHAR(50) NOT NULL CHECK (payment_method IN ('Cash on Pickup / Delivery', 'GCash Transfer')),
    payment_status VARCHAR(20) NOT NULL DEFAULT 'unpaid' CHECK (payment_status IN ('unpaid', 'pending', 'paid', 'failed', 'cancelled')),
    payment_reference VARCHAR(100),
    payment_deadline_at TIMESTAMP NULL,
    total_amount NUMERIC(10, 2) NOT NULL DEFAULT 0.00 CHECK (total_amount >= 0),
    order_status VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (order_status IN ('pending', 'completed', 'cancelled')),
    created_by_user_id INT NULL REFERENCES users(id),
    is_deleted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL
);

-- 5. Order Items Table
CREATE TABLE order_items (
    id SERIAL PRIMARY KEY,
    order_id INT NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    product_id INT NULL REFERENCES products(id) ON DELETE RESTRICT,
    product_id_snapshot INT NULL,
    product_name_snapshot VARCHAR(120) NULL,
    product_unit_snapshot VARCHAR(20) NULL,
    product_sku_snapshot VARCHAR(40) NULL,
    quantity NUMERIC(10, 2) NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL CHECK (unit_price >= 0),
    subtotal NUMERIC(10, 2) NOT NULL CHECK (subtotal >= 0)
);

-- 6. FIFO Batch Deductions Audit Table
CREATE TABLE batch_deductions (
    id SERIAL PRIMARY KEY,
    order_item_id INT NOT NULL REFERENCES order_items(id) ON DELETE RESTRICT,
    batch_id INT NOT NULL REFERENCES inventory_batches(id) ON DELETE RESTRICT,
    deducted_qty NUMERIC(10, 2) NOT NULL CHECK (deducted_qty > 0),
    deducted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. Soft-Delete Archive Tables
CREATE TABLE archived_orders (
    archive_id SERIAL PRIMARY KEY,
    original_order_id INT NOT NULL,
    reference_id VARCHAR(50) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL,
    archived_reason TEXT DEFAULT 'Soft-deleted by Admin',
    archived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE archived_batches (
    archive_id SERIAL PRIMARY KEY,
    original_batch_id INT NOT NULL,
    batch_number VARCHAR(50) NOT NULL,
    product_id INT NOT NULL,
    remaining_qty NUMERIC(10, 2) NOT NULL,
    archived_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8. Live In-Stock View (Supplies customer store and admin dashboard)
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