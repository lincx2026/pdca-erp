ALTER TABLE pdm_material ADD COLUMN reference_price NUMERIC(18, 4);
ALTER TABLE pdm_material ADD COLUMN search_keywords VARCHAR(500);
ALTER TABLE pdm_material ADD COLUMN reference_supplier_name VARCHAR(200);
ALTER TABLE pdm_material ADD COLUMN reference_supplier_pn VARCHAR(100);
ALTER TABLE pdm_material ADD CONSTRAINT pdm_material_reference_price_nonnegative CHECK (reference_price IS NULL OR reference_price >= 0);
ALTER TABLE scm_purchase_requisition_line ADD COLUMN estimate_source VARCHAR(20);
ALTER TABLE scm_purchase_requisition_line ADD COLUMN estimate_currency VARCHAR(10);
