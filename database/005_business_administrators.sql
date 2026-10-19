-- Adds business-scoped representatives who can approve only their own business side.
-- Apply once, after 004_provider_transfer_wait.sql.
USE machi_next;

ALTER TABLE role
  DROP CHECK ck_role_code,
  ADD CONSTRAINT ck_role_code CHECK (role_code IN ('ADMIN', 'BUSINESS_ADMIN', 'PROVEEDOR', 'CLIENTE'));

INSERT INTO role (role_code) VALUES ('BUSINESS_ADMIN');

CREATE TABLE business_admin (
  business_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  assigned_by_user_id BIGINT UNSIGNED NOT NULL,
  assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  revoked_at DATETIME(6) NULL,
  PRIMARY KEY (business_id, user_id),
  CONSTRAINT fk_business_admin_business FOREIGN KEY (business_id) REFERENCES business (business_id),
  CONSTRAINT fk_business_admin_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
  CONSTRAINT fk_business_admin_assigner FOREIGN KEY (assigned_by_user_id) REFERENCES app_user (user_id),
  INDEX ix_business_admin_user_active (user_id, revoked_at)
) ENGINE = InnoDB;
