-- Datos ficticios para desarrollo; ejecutar una sola vez después de 001_schema.sql.
-- Las cuentas quedan INACTIVE y su password_hash es un marcador, no un hash válido.
-- No habilitar estas cuentas ni reutilizar este archivo en producción.

USE machi_next;

INSERT INTO role (role_id, role_code) VALUES
  (1, 'ADMIN'),
  (2, 'PROVEEDOR'),
  (3, 'CLIENTE');

INSERT INTO business (business_id, legal_name, status, created_at)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n, CONCAT('Negocio de demostración ', LPAD(n, 2, '0')), 'ACTIVE', '2025-01-01 08:00:00'
FROM seq;

INSERT INTO app_user (user_id, email, password_hash, display_name, account_status, created_at)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 41
)
SELECT n,
       CONCAT('demo', LPAD(n, 2, '0'), '@example.test'),
       'NO_LOGIN_DEMO_SEED',
       CASE
         WHEN n = 1 THEN 'Administrador de demostración'
         WHEN n <= 21 THEN CONCAT('Proveedor de demostración ', LPAD(n - 1, 2, '0'))
         ELSE CONCAT('Cliente de demostración ', LPAD(n - 21, 2, '0'))
       END,
       'INACTIVE',
       '2025-01-01 08:00:00'
FROM seq;

INSERT INTO user_role (user_id, role_id) VALUES (1, 1);

INSERT INTO user_role (user_id, role_id)
WITH RECURSIVE seq (n) AS (
  SELECT 2 UNION ALL SELECT n + 1 FROM seq WHERE n < 21
)
SELECT n, 2 FROM seq;

INSERT INTO user_role (user_id, role_id)
WITH RECURSIVE seq (n) AS (
  SELECT 22 UNION ALL SELECT n + 1 FROM seq WHERE n < 41
)
SELECT n, 3 FROM seq;

INSERT INTO provider_assignment
  (assignment_id, provider_user_id, business_id, started_at, ended_at)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n, n + 1, n, '2025-01-02 08:00:00', NULL
FROM seq;

INSERT INTO provider_transfer
  (transfer_id, provider_user_id, from_business_id, to_business_id, requested_at, status)
VALUES
  (1, 2, 1, 2, '2025-02-01 09:00:00', 'PENDING'),
  (2, 3, 2, 3, '2025-02-02 09:00:00', 'PENDING');

INSERT INTO category (category_id, name) VALUES
  (1, 'Herramientas eléctricas'),
  (2, 'Elevación y acceso'),
  (3, 'Compactación'),
  (4, 'Corte y demolición'),
  (5, 'Medición');

INSERT INTO equipment_model
  (model_id, category_id, manufacturer, model_name, description)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n, MOD(n - 1, 5) + 1, 'Marca demostración',
       CONCAT('Modelo ', LPAD(n, 2, '0')),
       CONCAT('Modelo ficticio de prueba ', LPAD(n, 2, '0'))
FROM seq;

INSERT INTO equipment_unit
  (equipment_id, business_id, model_id, serial_number, operational_status, created_at)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n, n, n, CONCAT('DEMO-', LPAD(n, 4, '0')), 'AVAILABLE', '2025-01-03 08:00:00'
FROM seq;

INSERT INTO daily_rate
  (rate_id, equipment_id, daily_amount, deposit_amount, currency, valid_from, valid_to)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n, n, 25000.00 + (n * 1000.00), 100000.00 + (n * 5000.00), 'COP', '2025-01-01', NULL
FROM seq;

INSERT INTO reservation
  (reservation_id, customer_user_id, business_id, status, created_at)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n, n + 21, n, 'COMPLETED', '2025-01-05 08:00:00'
FROM seq;

INSERT INTO reservation
  (reservation_id, customer_user_id, business_id, status, created_at, cancelled_at,
   cancellation_reason, refund_percent)
VALUES
  (21, 22, 1, 'CANCELLED', '2025-02-01 08:00:00', '2025-02-08 08:00:00',
   'Cancelación con más de 24 horas de anticipación', 100),
  (22, 23, 2, 'CANCELLED', '2025-02-08 08:00:00', '2025-02-09 12:00:00',
   'Cancelación con menos de 24 horas de anticipación', 90);

INSERT INTO reservation_item
  (reservation_item_id, reservation_id, equipment_id, rate_id, start_date, return_date, scheduled_start_at,
   daily_amount_snapshot, deposit_snapshot, rental_amount_snapshot)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n, n, n, n, '2025-01-10', '2025-01-12', '2025-01-10 08:00:00',
       25000.00 + (n * 1000.00),
       100000.00 + (n * 5000.00),
       (25000.00 + (n * 1000.00)) * 3
FROM seq;

INSERT INTO reservation_item
  (reservation_item_id, reservation_id, equipment_id, rate_id, start_date, return_date,
   scheduled_start_at, daily_amount_snapshot, deposit_snapshot, rental_amount_snapshot)
VALUES
  (21, 21, 1, 1, '2025-02-10', '2025-02-12', '2025-02-10 08:00:00', 26000.00, 105000.00, 78000.00),
  (22, 22, 2, 2, '2025-02-10', '2025-02-12', '2025-02-10 08:00:00', 27000.00, 110000.00, 81000.00);

INSERT INTO rental
  (rental_id, reservation_item_id, handed_over_by_user_id, handed_over_at,
   returned_to_user_id, returned_at, return_condition)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n, n, n + 1, '2025-01-10 08:00:00', n + 1, '2025-01-12 17:00:00',
       CASE WHEN n <= 2 THEN 'TOTAL_DAMAGE'
            WHEN n <= 5 THEN 'PARTIAL_DAMAGE'
            ELSE 'GOOD' END
FROM seq;

INSERT INTO damage_report
  (damage_report_id, rental_id, reported_by_user_id, reviewed_by_user_id, reported_at,
   assessed_amount, retained_amount, status, decision_note)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 5
)
SELECT n, n, n + 1, 1, '2025-01-12 17:15:00',
       CASE WHEN n <= 2 THEN 100000.00 + (n * 5000.00) ELSE 10000.00 * n END,
       CASE WHEN n <= 2 THEN 100000.00 + (n * 5000.00) ELSE 10000.00 * n END,
       'APPROVED',
       'Daño ficticio de demostración; revisar evidencia.'
FROM seq;

INSERT INTO damage_evidence
  (evidence_id, damage_report_id, uploaded_by_user_id, storage_key, media_type, uploaded_at)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 5
)
SELECT n, n, n + 1, CONCAT('demo-placeholder/damage-', LPAD(n, 2, '0'), '.jpg'),
       'image/jpeg', '2025-01-12 17:16:00'
FROM seq;

-- Cargos de renta: 20 filas.
INSERT INTO payment_operation
  (payment_operation_id, reservation_id, operation_type, idempotency_key, amount, currency, status, created_at)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n, n, 'RENT_CHARGE', CONCAT('demo-rent-', n),
       (25000.00 + (n * 1000.00)) * 3, 'COP', 'SUCCEEDED', '2025-01-05 08:01:00'
FROM seq;

-- Cargos de depósito: 20 filas.
INSERT INTO payment_operation
  (payment_operation_id, reservation_id, operation_type, idempotency_key, amount, currency, status, created_at)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n + 20, n, 'DEPOSIT_CHARGE', CONCAT('demo-deposit-', n),
       100000.00 + (n * 5000.00), 'COP', 'SUCCEEDED', '2025-01-05 08:02:00'
FROM seq;

-- Depósitos devueltos sin daños: 15 filas, una por cada alquiler sin reporte.
INSERT INTO payment_operation
  (payment_operation_id, reservation_id, reverses_operation_id, operation_type,
   idempotency_key, amount, currency, status, created_at)
WITH RECURSIVE seq (n) AS (
  SELECT 6 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n + 35, n, n + 20, 'DEPOSIT_REFUND', CONCAT('demo-deposit-refund-', n),
       100000.00 + (n * 5000.00), 'COP', 'SUCCEEDED', '2025-01-12 17:30:00'
FROM seq;

-- Retenciones aprobadas por daños: 5 filas; cada una referencia el depósito cobrado.
INSERT INTO payment_operation
  (payment_operation_id, reservation_id, reverses_operation_id, operation_type,
   idempotency_key, amount, currency, status, created_at)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 5
)
SELECT n + 55, n, n + 20, 'DEPOSIT_RETENTION', CONCAT('demo-deposit-retention-', n),
       CASE WHEN n <= 2 THEN 100000.00 + (n * 5000.00) ELSE 10000.00 * n END,
       'COP', 'SUCCEEDED', '2025-01-12 17:30:00'
FROM seq;

-- Saldo devuelto en daños parciales: 3 filas; en daño total no hay saldo que devolver.
INSERT INTO payment_operation
  (payment_operation_id, reservation_id, reverses_operation_id, operation_type,
   idempotency_key, amount, currency, status, created_at)
WITH RECURSIVE seq (n) AS (
  SELECT 3 UNION ALL SELECT n + 1 FROM seq WHERE n < 5
)
SELECT n + 58, n, n + 20, 'DEPOSIT_REFUND', CONCAT('demo-damage-balance-refund-', n),
       (100000.00 + (n * 5000.00)) - (10000.00 * n),
       'COP', 'SUCCEEDED', '2025-01-12 17:31:00'
FROM seq;

INSERT INTO invoice
  (invoice_id, business_id, reservation_id, invoice_number, total_amount, currency, issued_at, status)
WITH RECURSIVE seq (n) AS (
  SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < 20
)
SELECT n, n, n, CONCAT('DEMO-', LPAD(n, 6, '0')),
       (25000.00 + (n * 1000.00)) * 3, 'COP', '2025-01-05 08:03:00', 'ISSUED'
FROM seq;

-- Movimientos simulados para las dos cancelaciones: renta y depósito, luego los reembolsos.
INSERT INTO payment_operation
  (payment_operation_id, reservation_id, operation_type, idempotency_key, amount, currency, status, created_at)
VALUES
  (64, 21, 'RENT_CHARGE', 'demo-cancel-rent-charge-21', 78000.00, 'COP', 'SUCCEEDED', '2025-02-01 08:01:00'),
  (65, 22, 'RENT_CHARGE', 'demo-cancel-rent-charge-22', 81000.00, 'COP', 'SUCCEEDED', '2025-02-08 08:01:00'),
  (66, 21, 'DEPOSIT_CHARGE', 'demo-cancel-deposit-charge-21', 105000.00, 'COP', 'SUCCEEDED', '2025-02-01 08:02:00'),
  (67, 22, 'DEPOSIT_CHARGE', 'demo-cancel-deposit-charge-22', 110000.00, 'COP', 'SUCCEEDED', '2025-02-08 08:02:00');

INSERT INTO payment_operation
  (payment_operation_id, reservation_id, reverses_operation_id, operation_type,
   idempotency_key, amount, currency, status, created_at)
VALUES
  (68, 21, 64, 'RENT_REFUND', 'demo-cancel-rent-refund-21', 78000.00, 'COP', 'SUCCEEDED', '2025-02-08 08:01:00'),
  (69, 22, 65, 'RENT_REFUND', 'demo-cancel-rent-refund-22', 72900.00, 'COP', 'SUCCEEDED', '2025-02-09 12:01:00'),
  (70, 21, 66, 'DEPOSIT_REFUND', 'demo-cancel-deposit-refund-21', 105000.00, 'COP', 'SUCCEEDED', '2025-02-08 08:02:00'),
  (71, 22, 67, 'DEPOSIT_REFUND', 'demo-cancel-deposit-refund-22', 110000.00, 'COP', 'SUCCEEDED', '2025-02-09 12:02:00');
