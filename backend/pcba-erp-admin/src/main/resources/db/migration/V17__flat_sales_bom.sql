ALTER TABLE pdm_bom ALTER COLUMN product_material_id DROP NOT NULL;
ALTER TABLE pdm_bom ADD CONSTRAINT pdm_bom_product_required CHECK (bom_type = 3 OR product_material_id IS NOT NULL);
ALTER TABLE pdm_bom_line ALTER COLUMN parent_material_id DROP NOT NULL;
CREATE UNIQUE INDEX pdm_bom_flat_sales_version_uk ON pdm_bom (COALESCE(customer_id, 0), lower(trim(kit_model)), lower(trim(version))) WHERE bom_type=3 AND product_material_id IS NULL;
CREATE UNIQUE INDEX pdm_bom_flat_sales_default_uk ON pdm_bom (COALESCE(customer_id, 0), lower(trim(kit_model))) WHERE bom_type=3 AND product_material_id IS NULL AND is_default=1;
CREATE UNIQUE INDEX pdm_bom_flat_component_uk ON pdm_bom_line (bom_id, component_material_id) WHERE parent_material_id IS NULL;
