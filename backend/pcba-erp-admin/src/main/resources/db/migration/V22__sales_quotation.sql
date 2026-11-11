CREATE TABLE sal_quotation (
  quotation_id BIGSERIAL PRIMARY KEY,
  quotation_no VARCHAR(50) NOT NULL UNIQUE,
  title VARCHAR(200) NOT NULL,
  customer_id BIGINT NOT NULL,
  currency VARCHAR(10) NOT NULL,
  valid_from DATE NOT NULL,
  valid_to DATE NOT NULL,
  status INTEGER NOT NULL DEFAULT 0,
  remark VARCHAR(500),
  audit_by VARCHAR(64),
  audit_time TIMESTAMP,
  reject_reason VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
ALTER TABLE sal_customer_price ADD COLUMN quotation_id BIGINT REFERENCES sal_quotation(quotation_id);
CREATE INDEX sal_customer_price_quotation_idx ON sal_customer_price(quotation_id);
