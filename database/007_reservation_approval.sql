-- A reservation occupies the equipment calendar while the provider decides.
ALTER TABLE reservation DROP CHECK ck_reservation_status;
ALTER TABLE reservation
  ADD CONSTRAINT ck_reservation_status
  CHECK (status IN ('PENDING_PROVIDER', 'CONFIRMED', 'REJECTED', 'IN_RENTAL', 'CANCELLED', 'COMPLETED'));

ALTER TABLE reservation
  ADD COLUMN provider_decided_by_user_id BIGINT UNSIGNED NULL AFTER refund_percent,
  ADD COLUMN provider_decided_at DATETIME(6) NULL AFTER provider_decided_by_user_id,
  ADD CONSTRAINT fk_reservation_provider_decision_user
    FOREIGN KEY (provider_decided_by_user_id) REFERENCES app_user (user_id);

ALTER TABLE invoice DROP CHECK ck_invoice_status;
ALTER TABLE invoice
  ADD CONSTRAINT ck_invoice_status CHECK (status IN ('PENDING', 'ISSUED', 'CANCELLED'));
