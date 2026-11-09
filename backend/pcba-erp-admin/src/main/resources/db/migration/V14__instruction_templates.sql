CREATE TABLE pmm_instruction_template (
    template_id BIGSERIAL PRIMARY KEY,
    material_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    content JSONB NOT NULL,
    attachments JSONB NOT NULL,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX pmm_instruction_template_material ON pmm_instruction_template(material_id);
-- Templates and work-order snapshots reference the same immutable object; each has its own attachment row.
ALTER TABLE pmm_work_instruction_attachment DROP CONSTRAINT pmm_work_instruction_attachment_object_key_key;
