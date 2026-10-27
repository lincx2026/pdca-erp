-- Role/department status is now consistently 1=enabled, 0=disabled.
-- Only the verified legacy import used the opposite meaning. Fresh V2 seeds
-- already use the new meaning and must keep their existing status values.
-- Flyway executes this conversion once, inside its migration transaction.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM flyway_schema_history
               WHERE version = '7' AND type = 'BASELINE' AND success
                 AND description = 'Verified MySQL import') THEN
        UPDATE sys_role SET status = 1 - status WHERE status IN (0, 1);
        UPDATE sys_dept SET status = 1 - status WHERE status IN (0, 1);
    END IF;
END $$;

ALTER TABLE sys_role ALTER COLUMN status SET DEFAULT 1;
ALTER TABLE sys_dept ALTER COLUMN status SET DEFAULT 1;
COMMENT ON COLUMN sys_role.status IS '1=正常, 0=停用';
COMMENT ON COLUMN sys_dept.status IS '1=正常, 0=停用';
