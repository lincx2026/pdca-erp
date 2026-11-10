ALTER TABLE scm_purchase_order ADD COLUMN printed_at TIMESTAMP;
ALTER TABLE scm_purchase_order ADD COLUMN printed_by VARCHAR(64);
CREATE TABLE scm_purchase_order_print_log (
    id BIGSERIAL PRIMARY KEY,
    po_id BIGINT NOT NULL,
    confirmed_by VARCHAR(64),
    confirmed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
