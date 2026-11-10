-- Existing dates stay unchanged; NULL represents a quote without an expiry date.
ALTER TABLE scm_supplier_price ALTER COLUMN valid_to DROP NOT NULL;
