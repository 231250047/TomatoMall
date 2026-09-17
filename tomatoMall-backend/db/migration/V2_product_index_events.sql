-- Apply after V1. Supports PRODUCT_CHANGED even on installations whose Hibernate DDL
-- previously narrowed this field to a native MySQL ENUM. No product/stock data is changed.
ALTER TABLE outbox_event MODIFY COLUMN kind VARCHAR(32) NOT NULL;
