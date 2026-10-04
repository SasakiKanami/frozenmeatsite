-- Seed Admin & Cashier User
INSERT INTO users (full_name, username, password_hash, role) VALUES
('Virna R. Jallorina', 'admin_virna', '$2b$12$K8y5R5qFhQjN/samplebcryptsecuredhashvalue', 'admin'),
('Cashier Storefront', 'cashier_01', '$2b$12$K8y5R5qFhQjN/samplebcryptsecuredhashvalue', 'cashier');

-- Seed Catalog Items with Initial Picture Links
INSERT INTO products (name, category, temperature_tier, unit, price_per_unit, image_url) VALUES
('Pork Liempo Cut', 'Pork', 'Fresh Chilled', 'kg', 250.00, '../../assets/pictures/pork_liempo.png'),
('Pork Chop Cut', 'Pork', 'Fresh Chilled', 'kg', 220.00, '../../assets/pictures/pork_chop.png'),
('Chicken Breast Fillet', 'Poultry', 'Fresh Chilled', 'kg', 210.00, NULL),
('Beef Bulalo / Shank', 'Beef', 'Deep Freeze', 'kg', 380.00, NULL),
('Tender Juicy Hotdog', 'Processed', 'Processed Pack', 'pack', 200.00, '../../assets/pictures/tj_hotdog.jpg'),
('Young Pork Tocino', 'Processed', 'Processed Pack', 'pack', 75.00, NULL);

-- Seed Incoming Batches (Staggered arrival for FIFO testing)
INSERT INTO inventory_batches (product_id, batch_number, supplier_name, initial_qty, remaining_qty, arrival_date) VALUES
(1, 'BATCH-PL-001', 'Monterey Meat Plant', 15.00, 15.00, NOW() - INTERVAL '3 days'),
(1, 'BATCH-PL-002', 'Monterey Meat Plant', 20.00, 20.00, NOW()),
(2, 'BATCH-PC-001', 'Monterey Meat Plant', 10.00, 10.00, NOW() - INTERVAL '2 days'),
(3, 'BATCH-CB-001', 'Magnolia Poultry', 12.00, 12.00, NOW() - INTERVAL '1 day'),
(4, 'BATCH-BS-001', 'Batangas Livestock Farm', 8.00, 8.00, NOW() - INTERVAL '4 days'),
(5, 'BATCH-TJ-001', 'San Miguel Foods', 25.00, 25.00, NOW() - INTERVAL '5 days'),
(6, 'BATCH-TO-001', 'CDO Foodshpere', 30.00, 30.00, NOW() - INTERVAL '3 days');