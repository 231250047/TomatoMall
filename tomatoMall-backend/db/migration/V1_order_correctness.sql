-- Run against a BACKUP/CLONE first, with application writes stopped. MySQL 8.0.16+.
-- This is an explicit operator-run migration, not auto-executed by Spring Boot.
-- Historical PENDING orders used Redis reservations. Do not manufacture snapshots from today's carts.
DELIMITER //
CREATE PROCEDURE migrate_order_correctness()
BEGIN
    IF EXISTS(SELECT 1 FROM orders WHERE status IN ('PENDING','CLOSING')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reconcile all legacy open orders with payment provider and inventory before migration';
    END IF;
    IF EXISTS(SELECT 1 FROM stockpiles WHERE amount < 0 OR frozen <> 0) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Reconcile legacy frozen/negative inventory before migration';
    END IF;
    ALTER TABLE orders
        ADD COLUMN request_id VARCHAR(64) NULL,
        ADD COLUMN request_hash VARCHAR(64) NULL,
        ADD COLUMN expires_at DATETIME(6) NULL,
        ADD COLUMN next_check_at DATETIME(6) NULL,
        ADD COLUMN payment_attempted BIT NOT NULL DEFAULT 0,
        ADD COLUMN cancel_requested BIT NOT NULL DEFAULT 0,
        ADD COLUMN close_attempts INT NOT NULL DEFAULT 0,
        ADD COLUMN trade_no VARCHAR(100) NULL,
        ADD COLUMN payment_incident VARCHAR(255) NULL,
        ADD COLUMN hidden BIT NOT NULL DEFAULT 0,
        ADD UNIQUE KEY uq_order_request (user_id, request_id),
        ADD UNIQUE KEY uq_order_trade (trade_no),
        ADD KEY idx_order_due (status, next_check_at);
    ALTER TABLE stockpiles ADD CONSTRAINT ck_stock_bounds CHECK(amount >= 0 AND frozen >= 0 AND frozen <= amount);
    CREATE TABLE outbox_event (
        id VARCHAR(36) NOT NULL PRIMARY KEY,
        kind VARCHAR(32) NOT NULL,
        aggregate_id INT NOT NULL,
        deliver_at DATETIME(6) NOT NULL,
        next_attempt_at DATETIME(6) NOT NULL,
        sent BIT NOT NULL DEFAULT 0,
        attempts INT NOT NULL DEFAULT 0,
        lease_token VARCHAR(36),
        lease_until DATETIME(6),
        last_error VARCHAR(100),
        KEY idx_outbox_due (sent,next_attempt_at)
    ) ENGINE=InnoDB;
END//
DELIMITER ;
CALL migrate_order_correctness();
DROP PROCEDURE migrate_order_correctness;
-- Deliberately one-shot. Do not rerun after a partially applied DDL; inspect schema and finish manually.
-- Existing payment_time, receiver_* and order_item belong to the pre-existing application's entity schema.
-- The old tomatomall.sql dump lacks some of them: use V0_legacy_entity_columns.sql FIRST if bootstrapping that dump.
