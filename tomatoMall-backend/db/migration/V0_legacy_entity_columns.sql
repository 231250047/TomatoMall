-- ONLY for the old repository SQL dump. Do not run if these columns already exist.
ALTER TABLE orders
    ADD COLUMN payment_time DATETIME(6) NULL,
    ADD COLUMN receiver_name VARCHAR(50) NULL,
    ADD COLUMN receiver_phone VARCHAR(20) NULL,
    ADD COLUMN receiver_address VARCHAR(255) NULL,
    ADD COLUMN receiver_postal_code VARCHAR(10) NULL;
CREATE TABLE IF NOT EXISTS order_item (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id INT,
    product_id INT,
    title VARCHAR(255),
    cover VARCHAR(255),
    price DECIMAL(38,2),
    quantity INT,
    KEY idx_item_order (order_id)
) ENGINE=InnoDB;
-- Missing historical snapshots cannot be reconstructed reliably from a mutable cart.
