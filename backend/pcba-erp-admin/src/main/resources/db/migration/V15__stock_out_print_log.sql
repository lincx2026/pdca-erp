ALTER TABLE wms_stock_out ADD COLUMN printed_at TIMESTAMP;
ALTER TABLE wms_stock_out ADD COLUMN printed_by VARCHAR(64);
CREATE TABLE wms_stock_out_print_log (
    id BIGSERIAL PRIMARY KEY,
    stock_out_id BIGINT NOT NULL REFERENCES wms_stock_out(stock_out_id) ON DELETE CASCADE,
    confirmed_by VARCHAR(64),
    confirmed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
