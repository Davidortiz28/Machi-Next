-- Adds the agreed three-day waiting period after both businesses approve.
-- Apply once, after 003_provider_transfer_approvals.sql.
USE machi_next;

ALTER TABLE provider_transfer
  DROP CHECK ck_transfer_status,
  ADD COLUMN approved_at DATETIME(6) NULL AFTER to_business_decided_by_user_id,
  ADD COLUMN effective_at DATETIME(6) NULL AFTER approved_at,
  ADD COLUMN completed_at DATETIME(6) NULL AFTER effective_at,
  ADD COLUMN completed_by_user_id BIGINT UNSIGNED NULL AFTER completed_at,
  ADD CONSTRAINT fk_transfer_completer FOREIGN KEY (completed_by_user_id) REFERENCES app_user (user_id),
  ADD CONSTRAINT ck_transfer_status CHECK (status IN ('PENDING', 'WAITING', 'COMPLETED', 'REJECTED', 'CANCELLED'));
