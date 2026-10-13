-- Machi-Next: esquema inicial para MySQL 8.4 LTS.
-- Ejecutar en una instancia de desarrollo vacía. No elimina ni sobrescribe datos.

CREATE DATABASE IF NOT EXISTS machi_next
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE machi_next;

CREATE TABLE IF NOT EXISTS app_user (
  user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email VARCHAR(254) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(160) NOT NULL,
  account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (user_id),
  CONSTRAINT uk_app_user_email UNIQUE (email),
  CONSTRAINT ck_app_user_status CHECK (account_status IN ('ACTIVE', 'INACTIVE', 'LOCKED'))
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS role (
  role_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role_code VARCHAR(32) NOT NULL,
  PRIMARY KEY (role_id),
  CONSTRAINT uk_role_code UNIQUE (role_code),
  CONSTRAINT ck_role_code CHECK (role_code IN ('ADMIN', 'PROVEEDOR', 'CLIENTE'))
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS user_role (
  user_id BIGINT UNSIGNED NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (user_id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role (role_id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS business (
  business_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  legal_name VARCHAR(180) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (business_id),
  CONSTRAINT ck_business_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED'))
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS provider_assignment (
  assignment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  provider_user_id BIGINT UNSIGNED NOT NULL,
  business_id BIGINT UNSIGNED NOT NULL,
  started_at DATETIME(6) NOT NULL,
  ended_at DATETIME(6) NULL,
  current_provider_user_id BIGINT UNSIGNED
    GENERATED ALWAYS AS (CASE WHEN ended_at IS NULL THEN provider_user_id ELSE NULL END) STORED,
  PRIMARY KEY (assignment_id),
  CONSTRAINT fk_assignment_provider FOREIGN KEY (provider_user_id) REFERENCES app_user (user_id),
  CONSTRAINT fk_assignment_business FOREIGN KEY (business_id) REFERENCES business (business_id),
  CONSTRAINT ck_assignment_dates CHECK (ended_at IS NULL OR ended_at >= started_at),
  CONSTRAINT uk_provider_current_assignment UNIQUE (current_provider_user_id),
  INDEX ix_assignment_business_current (business_id, ended_at)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS provider_transfer (
  transfer_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  provider_user_id BIGINT UNSIGNED NOT NULL,
  from_business_id BIGINT UNSIGNED NOT NULL,
  to_business_id BIGINT UNSIGNED NOT NULL,
  requested_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  resolved_at DATETIME(6) NULL,
  resolved_by_user_id BIGINT UNSIGNED NULL,
  PRIMARY KEY (transfer_id),
  CONSTRAINT fk_transfer_provider FOREIGN KEY (provider_user_id) REFERENCES app_user (user_id),
  CONSTRAINT fk_transfer_from_business FOREIGN KEY (from_business_id) REFERENCES business (business_id),
  CONSTRAINT fk_transfer_to_business FOREIGN KEY (to_business_id) REFERENCES business (business_id),
  CONSTRAINT fk_transfer_resolver FOREIGN KEY (resolved_by_user_id) REFERENCES app_user (user_id),
  CONSTRAINT ck_transfer_different_business CHECK (from_business_id <> to_business_id),
  CONSTRAINT ck_transfer_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  INDEX ix_transfer_provider_status (provider_user_id, status)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS category (
  category_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  PRIMARY KEY (category_id),
  CONSTRAINT uk_category_name UNIQUE (name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS equipment_model (
  model_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  category_id BIGINT UNSIGNED NOT NULL,
  manufacturer VARCHAR(120) NOT NULL,
  model_name VARCHAR(160) NOT NULL,
  description VARCHAR(1000) NULL,
  PRIMARY KEY (model_id),
  CONSTRAINT fk_model_category FOREIGN KEY (category_id) REFERENCES category (category_id),
  CONSTRAINT uk_equipment_model UNIQUE (category_id, manufacturer, model_name),
  INDEX ix_equipment_model_name (model_name)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS equipment_unit (
  equipment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  business_id BIGINT UNSIGNED NOT NULL,
  model_id BIGINT UNSIGNED NOT NULL,
  serial_number VARCHAR(100) NOT NULL,
  operational_status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (equipment_id),
  CONSTRAINT fk_equipment_business FOREIGN KEY (business_id) REFERENCES business (business_id),
  CONSTRAINT fk_equipment_model FOREIGN KEY (model_id) REFERENCES equipment_model (model_id),
  CONSTRAINT uk_equipment_serial_per_business UNIQUE (business_id, serial_number),
  CONSTRAINT uk_equipment_business_pair UNIQUE (equipment_id, business_id),
  CONSTRAINT ck_equipment_status CHECK (operational_status IN ('AVAILABLE', 'MAINTENANCE', 'RETIRED')),
  INDEX ix_equipment_business_status (business_id, operational_status),
  INDEX ix_equipment_model_search (model_id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS daily_rate (
  rate_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  equipment_id BIGINT UNSIGNED NOT NULL,
  daily_amount DECIMAL(12,2) NOT NULL,
  deposit_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  currency CHAR(3) NOT NULL DEFAULT 'COP',
  valid_from DATE NOT NULL,
  valid_to DATE NULL,
  PRIMARY KEY (rate_id),
  CONSTRAINT fk_rate_equipment FOREIGN KEY (equipment_id) REFERENCES equipment_unit (equipment_id),
  CONSTRAINT uk_rate_equipment_pair UNIQUE (equipment_id, rate_id),
  CONSTRAINT ck_rate_amounts CHECK (daily_amount >= 0 AND deposit_amount >= 0),
  CONSTRAINT ck_rate_dates CHECK (valid_to IS NULL OR valid_to > valid_from),
  INDEX ix_rate_equipment_dates (equipment_id, valid_from, valid_to)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS reservation (
  reservation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  customer_user_id BIGINT UNSIGNED NOT NULL,
  business_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  cancelled_at DATETIME(6) NULL,
  cancellation_reason VARCHAR(500) NULL,
  refund_percent TINYINT UNSIGNED NULL,
  PRIMARY KEY (reservation_id),
  CONSTRAINT fk_reservation_customer FOREIGN KEY (customer_user_id) REFERENCES app_user (user_id),
  CONSTRAINT fk_reservation_business FOREIGN KEY (business_id) REFERENCES business (business_id),
  CONSTRAINT ck_reservation_status CHECK (status IN ('CONFIRMED', 'IN_RENTAL', 'CANCELLED', 'COMPLETED')),
  CONSTRAINT ck_reservation_refund CHECK (refund_percent IS NULL OR refund_percent IN (90, 100)),
  INDEX ix_reservation_customer_created (customer_user_id, created_at),
  INDEX ix_reservation_business_status (business_id, status)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS reservation_item (
  reservation_item_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reservation_id BIGINT UNSIGNED NOT NULL,
  equipment_id BIGINT UNSIGNED NOT NULL,
  rate_id BIGINT UNSIGNED NOT NULL,
  start_date DATE NOT NULL,
  return_date DATE NOT NULL,
  scheduled_start_at DATETIME(6) NOT NULL,
  daily_amount_snapshot DECIMAL(12,2) NOT NULL,
  deposit_snapshot DECIMAL(12,2) NOT NULL,
  rental_amount_snapshot DECIMAL(12,2) NOT NULL,
  PRIMARY KEY (reservation_item_id),
  CONSTRAINT fk_reservation_item_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (reservation_id),
  CONSTRAINT fk_reservation_item_equipment FOREIGN KEY (equipment_id) REFERENCES equipment_unit (equipment_id),
  CONSTRAINT fk_reservation_item_rate FOREIGN KEY (equipment_id, rate_id)
    REFERENCES daily_rate (equipment_id, rate_id),
  CONSTRAINT uk_reservation_equipment UNIQUE (reservation_id, equipment_id),
  CONSTRAINT ck_reservation_item_dates CHECK (return_date >= start_date),
  CONSTRAINT ck_reservation_item_amounts CHECK (
    daily_amount_snapshot >= 0 AND deposit_snapshot >= 0 AND rental_amount_snapshot >= 0
  ),
  INDEX ix_item_availability (equipment_id, start_date, return_date)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS rental (
  rental_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reservation_item_id BIGINT UNSIGNED NOT NULL,
  handed_over_by_user_id BIGINT UNSIGNED NOT NULL,
  handed_over_at DATETIME(6) NOT NULL,
  returned_to_user_id BIGINT UNSIGNED NULL,
  returned_at DATETIME(6) NULL,
  return_condition VARCHAR(20) NULL,
  PRIMARY KEY (rental_id),
  CONSTRAINT uk_rental_reservation_item UNIQUE (reservation_item_id),
  CONSTRAINT fk_rental_item FOREIGN KEY (reservation_item_id) REFERENCES reservation_item (reservation_item_id),
  CONSTRAINT fk_rental_handover_user FOREIGN KEY (handed_over_by_user_id) REFERENCES app_user (user_id),
  CONSTRAINT fk_rental_return_user FOREIGN KEY (returned_to_user_id) REFERENCES app_user (user_id),
  CONSTRAINT ck_rental_return_fields CHECK (
    (returned_at IS NULL AND returned_to_user_id IS NULL AND return_condition IS NULL)
    OR (returned_at IS NOT NULL AND returned_to_user_id IS NOT NULL AND return_condition IS NOT NULL)
  ),
  CONSTRAINT ck_rental_condition CHECK (
    return_condition IS NULL OR return_condition IN ('GOOD', 'PARTIAL_DAMAGE', 'TOTAL_DAMAGE')
  )
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS damage_report (
  damage_report_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  rental_id BIGINT UNSIGNED NOT NULL,
  reported_by_user_id BIGINT UNSIGNED NOT NULL,
  reviewed_by_user_id BIGINT UNSIGNED NULL,
  reported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  assessed_amount DECIMAL(12,2) NULL,
  retained_amount DECIMAL(12,2) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'REPORTED',
  decision_note VARCHAR(1000) NULL,
  PRIMARY KEY (damage_report_id),
  CONSTRAINT fk_damage_rental FOREIGN KEY (rental_id) REFERENCES rental (rental_id),
  CONSTRAINT fk_damage_reporter FOREIGN KEY (reported_by_user_id) REFERENCES app_user (user_id),
  CONSTRAINT fk_damage_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES app_user (user_id),
  CONSTRAINT ck_damage_amounts CHECK (
    (assessed_amount IS NULL OR assessed_amount >= 0)
    AND (retained_amount IS NULL OR retained_amount >= 0)
    AND (assessed_amount IS NULL OR retained_amount IS NULL OR retained_amount <= assessed_amount)
  ),
  CONSTRAINT ck_damage_status CHECK (status IN ('REPORTED', 'APPROVED', 'REJECTED')),
  INDEX ix_damage_rental_status (rental_id, status)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS damage_evidence (
  evidence_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  damage_report_id BIGINT UNSIGNED NOT NULL,
  uploaded_by_user_id BIGINT UNSIGNED NOT NULL,
  storage_key VARCHAR(500) NOT NULL,
  media_type VARCHAR(100) NOT NULL,
  uploaded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (evidence_id),
  CONSTRAINT fk_evidence_report FOREIGN KEY (damage_report_id) REFERENCES damage_report (damage_report_id),
  CONSTRAINT fk_evidence_uploader FOREIGN KEY (uploaded_by_user_id) REFERENCES app_user (user_id),
  CONSTRAINT uk_evidence_storage_key UNIQUE (storage_key)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS payment_operation (
  payment_operation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  reservation_id BIGINT UNSIGNED NOT NULL,
  reverses_operation_id BIGINT UNSIGNED NULL,
  operation_type VARCHAR(24) NOT NULL,
  idempotency_key VARCHAR(100) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'COP',
  status VARCHAR(16) NOT NULL DEFAULT 'SUCCEEDED',
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (payment_operation_id),
  CONSTRAINT fk_payment_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (reservation_id),
  CONSTRAINT fk_payment_reversal FOREIGN KEY (reverses_operation_id) REFERENCES payment_operation (payment_operation_id),
  CONSTRAINT uk_payment_idempotency UNIQUE (idempotency_key),
  CONSTRAINT ck_payment_type CHECK (
    operation_type IN ('RENT_CHARGE', 'DEPOSIT_CHARGE', 'RENT_REFUND', 'DEPOSIT_REFUND', 'DEPOSIT_RETENTION')
  ),
  CONSTRAINT ck_payment_amount CHECK (amount > 0),
  CONSTRAINT ck_payment_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
  INDEX ix_payment_reservation_status (reservation_id, status)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS invoice (
  invoice_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  business_id BIGINT UNSIGNED NOT NULL,
  reservation_id BIGINT UNSIGNED NOT NULL,
  invoice_number VARCHAR(40) NOT NULL,
  total_amount DECIMAL(12,2) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'COP',
  issued_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  status VARCHAR(16) NOT NULL DEFAULT 'ISSUED',
  PRIMARY KEY (invoice_id),
  CONSTRAINT fk_invoice_business FOREIGN KEY (business_id) REFERENCES business (business_id),
  CONSTRAINT fk_invoice_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (reservation_id),
  CONSTRAINT uk_invoice_reservation UNIQUE (reservation_id),
  CONSTRAINT uk_invoice_business_number UNIQUE (business_id, invoice_number),
  CONSTRAINT ck_invoice_amount CHECK (total_amount >= 0),
  CONSTRAINT ck_invoice_status CHECK (status IN ('ISSUED', 'CANCELLED'))
) ENGINE = InnoDB;
