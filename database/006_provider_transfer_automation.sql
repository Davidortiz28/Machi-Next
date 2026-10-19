-- Distinguishes automatic transfer completion from an administrator-triggered completion.
-- Apply once, after 005_business_administrators.sql.
USE machi_next;

ALTER TABLE provider_transfer
  ADD COLUMN completion_method VARCHAR(16) NULL AFTER completed_by_user_id,
  ADD CONSTRAINT ck_transfer_completion_method
    CHECK (completion_method IS NULL OR completion_method IN ('ADMIN', 'SYSTEM'));

UPDATE provider_transfer
SET completion_method = CASE WHEN completed_by_user_id IS NULL THEN 'SYSTEM' ELSE 'ADMIN' END
WHERE status = 'COMPLETED';
