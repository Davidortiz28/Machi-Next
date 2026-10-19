-- Extends existing provider transfers with an explicit decision by each business.
-- Apply once to databases created before this migration; compose runs it on fresh volumes.
USE machi_next;

ALTER TABLE provider_transfer
  ADD COLUMN from_business_decision VARCHAR(16) NULL AFTER status,
  ADD COLUMN from_business_decided_at DATETIME(6) NULL AFTER from_business_decision,
  ADD COLUMN from_business_decided_by_user_id BIGINT UNSIGNED NULL AFTER from_business_decided_at,
  ADD COLUMN to_business_decision VARCHAR(16) NULL AFTER from_business_decided_by_user_id,
  ADD COLUMN to_business_decided_at DATETIME(6) NULL AFTER to_business_decision,
  ADD COLUMN to_business_decided_by_user_id BIGINT UNSIGNED NULL AFTER to_business_decided_at,
  ADD CONSTRAINT fk_transfer_from_decider FOREIGN KEY (from_business_decided_by_user_id) REFERENCES app_user (user_id),
  ADD CONSTRAINT fk_transfer_to_decider FOREIGN KEY (to_business_decided_by_user_id) REFERENCES app_user (user_id),
  ADD CONSTRAINT ck_transfer_from_decision CHECK (from_business_decision IS NULL OR from_business_decision IN ('APPROVED', 'REJECTED')),
  ADD CONSTRAINT ck_transfer_to_decision CHECK (to_business_decision IS NULL OR to_business_decision IN ('APPROVED', 'REJECTED'));
