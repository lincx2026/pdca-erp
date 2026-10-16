-- Preserve fields found in the existing database but absent from historical DDL.
ALTER TABLE pdm_material ADD COLUMN default_supplier1 BIGINT;
ALTER TABLE pdm_material ADD COLUMN default_supplier2 BIGINT;
ALTER TABLE pdm_material ADD COLUMN default_supplier3 BIGINT;
ALTER TABLE pdm_process_route ADD COLUMN product_code VARCHAR(64);
ALTER TABLE pdm_process_route ADD COLUMN product_name VARCHAR(200);
ALTER TABLE pdm_process_route ADD COLUMN bom_version_code VARCHAR(64);
