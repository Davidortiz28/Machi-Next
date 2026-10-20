package com.machinext.reservation.application;

import com.machinext.catalog.domain.RentalPeriod;
import com.machinext.reservation.domain.CancellationPolicy;
import com.machinext.reservation.domain.DepositSettlement;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService {
    private final JdbcTemplate jdbc;

    public ReservationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public ReservationResponse create(long customerId, long equipmentId, LocalDate startDate, LocalDate returnDate) {
        RentalPeriod period = new RentalPeriod(startDate, returnDate);
        if (startDate.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La fecha de inicio no puede estar en el pasado");
        }

        List<Long> equipmentLocks = jdbc.query("SELECT equipment_id FROM equipment_unit WHERE equipment_id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong(1), equipmentId);
        if (equipmentLocks.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese equipo");
        }

        List<Rate> rates = jdbc.query("""
                SELECT eu.business_id, dr.rate_id, dr.daily_amount, dr.deposit_amount, dr.currency
                FROM equipment_unit eu
                JOIN business b ON b.business_id = eu.business_id
                JOIN daily_rate dr ON dr.equipment_id = eu.equipment_id
                WHERE eu.equipment_id = ?
                  AND eu.operational_status = 'AVAILABLE'
                  AND b.status = 'ACTIVE'
                  AND dr.valid_from <= ?
                  AND (dr.valid_to IS NULL OR dr.valid_to >= ?)
                ORDER BY dr.valid_from DESC
                LIMIT 1
                """, (rs, rowNum) -> new Rate(rs.getLong("business_id"), rs.getLong("rate_id"),
                rs.getBigDecimal("daily_amount"), rs.getBigDecimal("deposit_amount"), rs.getString("currency")),
                equipmentId, startDate, returnDate);
        if (rates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El equipo no está operativo o no tiene tarifa vigente para esas fechas");
        }
        Integer conflicts = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM reservation_item ri
                JOIN reservation r ON r.reservation_id = ri.reservation_id
                WHERE ri.equipment_id = ?
                  AND r.status IN ('CONFIRMED', 'IN_RENTAL')
                  AND ri.start_date <= ?
                  AND ri.return_date >= ?
                """, Integer.class, equipmentId, returnDate, startDate);
        if (conflicts != null && conflicts > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El equipo ya está reservado para parte de ese período");
        }

        Rate rate = rates.getFirst();
        BigDecimal rentalAmount = rate.dailyAmount().multiply(BigDecimal.valueOf(period.calendarDaysInclusive()));
        jdbc.update("INSERT INTO reservation (customer_user_id, business_id, status) VALUES (?, ?, 'CONFIRMED')",
                customerId, rate.businessId());
        Long reservationId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("""
                INSERT INTO reservation_item
                  (reservation_id, equipment_id, rate_id, start_date, return_date, scheduled_start_at,
                   daily_amount_snapshot, deposit_snapshot, rental_amount_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, reservationId, equipmentId, rate.rateId(), startDate, returnDate,
                startDate.atStartOfDay(), rate.dailyAmount(), rate.depositAmount(), rentalAmount);
        recordPayment(reservationId, "RENT_CHARGE", "reservation:" + reservationId + ":rent:charge",
                rentalAmount, rate.currency());
        recordPayment(reservationId, "DEPOSIT_CHARGE", "reservation:" + reservationId + ":deposit:charge",
                rate.depositAmount(), rate.currency());
        return new ReservationResponse(reservationId, equipmentId, "CONFIRMED", startDate, returnDate,
                period.calendarDaysInclusive(), rate.dailyAmount(), rentalAmount, rate.depositAmount(), rate.currency(), null, null);
    }

    public List<ReservationResponse> listForCustomer(long customerId) {
        return jdbc.query("""
                SELECT r.reservation_id, ri.equipment_id, r.status, ri.start_date, ri.return_date,
                       DATEDIFF(ri.return_date, ri.start_date) + 1 AS calendar_days,
                       ri.daily_amount_snapshot, ri.rental_amount_snapshot, ri.deposit_snapshot, dr.currency,
                       r.refund_percent,
                       (SELECT COALESCE(SUM(po.amount), 0) FROM payment_operation po
                        WHERE po.reservation_id = r.reservation_id
                          AND po.operation_type IN ('RENT_REFUND', 'DEPOSIT_REFUND')) AS refund_amount
                FROM reservation r
                JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                JOIN daily_rate dr ON dr.equipment_id = ri.equipment_id AND dr.rate_id = ri.rate_id
                WHERE r.customer_user_id = ?
                ORDER BY r.created_at DESC, r.reservation_id DESC
                """, (rs, rowNum) -> new ReservationResponse(rs.getLong("reservation_id"),
                rs.getLong("equipment_id"), rs.getString("status"), rs.getDate("start_date").toLocalDate(),
                rs.getDate("return_date").toLocalDate(), rs.getLong("calendar_days"),
                rs.getBigDecimal("daily_amount_snapshot"), rs.getBigDecimal("rental_amount_snapshot"),
                rs.getBigDecimal("deposit_snapshot"), rs.getString("currency"),
                (Integer) rs.getObject("refund_percent"), rs.getBigDecimal("refund_amount")), customerId);
    }

    public List<ProviderRentalResponse> listForProvider(long providerId) {
        List<Long> businessIds = jdbc.query("""
                SELECT a.business_id FROM provider_assignment a
                JOIN business b ON b.business_id = a.business_id
                JOIN app_user u ON u.user_id = a.provider_user_id AND u.account_status = 'ACTIVE'
                JOIN user_role ur ON ur.user_id = u.user_id
                JOIN role ro ON ro.role_id = ur.role_id AND ro.role_code = 'PROVEEDOR'
                WHERE a.provider_user_id = ? AND a.ended_at IS NULL AND b.status = 'ACTIVE'
                """, (rs, rowNum) -> rs.getLong("business_id"), providerId);
        if (businessIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes un negocio activo asignado");
        }
        long businessId = businessIds.getFirst();
        return jdbc.query("""
                SELECT r.reservation_id, ri.equipment_id, r.status, ri.start_date, ri.return_date,
                       DATEDIFF(ri.return_date, ri.start_date) + 1 AS calendar_days,
                       ri.rental_amount_snapshot, ri.deposit_snapshot, dr.currency,
                       u.display_name AS customer_name, em.manufacturer, em.model_name,
                       re.return_condition, re.handed_over_by_user_id
                FROM reservation r
                JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                JOIN daily_rate dr ON dr.equipment_id = ri.equipment_id AND dr.rate_id = ri.rate_id
                JOIN equipment_unit eu ON eu.equipment_id = ri.equipment_id
                JOIN equipment_model em ON em.model_id = eu.model_id
                JOIN app_user u ON u.user_id = r.customer_user_id
                LEFT JOIN rental re ON re.reservation_item_id = ri.reservation_item_id
                WHERE r.business_id = ?
                ORDER BY CASE r.status WHEN 'IN_RENTAL' THEN 0 WHEN 'CONFIRMED' THEN 1 ELSE 2 END,
                         ri.start_date DESC, r.reservation_id DESC
                """, (rs, rowNum) -> new ProviderRentalResponse(rs.getLong("reservation_id"),
                rs.getLong("equipment_id"), rs.getString("status"), rs.getDate("start_date").toLocalDate(),
                rs.getDate("return_date").toLocalDate(), rs.getLong("calendar_days"),
                rs.getBigDecimal("rental_amount_snapshot"), rs.getBigDecimal("deposit_snapshot"),
                rs.getString("currency"), rs.getString("customer_name"), rs.getString("manufacturer"),
                rs.getString("model_name"), rs.getString("return_condition"),
                (Long) rs.getObject("handed_over_by_user_id")), businessId);
    }

    @Transactional
    public CancellationResponse cancel(long customerId, long reservationId, String reason) {
        List<CancellationData> rows = jdbc.query("""
                SELECT r.status, ri.scheduled_start_at, ri.rental_amount_snapshot, ri.deposit_snapshot, dr.currency
                FROM reservation r
                JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                JOIN daily_rate dr ON dr.equipment_id = ri.equipment_id AND dr.rate_id = ri.rate_id
                WHERE r.reservation_id = ? AND r.customer_user_id = ?
                FOR UPDATE
                """, (rs, rowNum) -> new CancellationData(rs.getString("status"),
                rs.getTimestamp("scheduled_start_at").toLocalDateTime(),
                rs.getBigDecimal("rental_amount_snapshot"), rs.getBigDecimal("deposit_snapshot"), rs.getString("currency")),
                reservationId, customerId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa reserva para este cliente");
        }
        CancellationData reservation = rows.getFirst();
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se pueden cancelar reservas confirmadas");
        }

        LocalDateTime cancelledAt = LocalDateTime.now();
        int refundPercent = CancellationPolicy.refundPercent(cancelledAt, reservation.scheduledStartAt());
        BigDecimal rentRefund = reservation.rentalAmount().multiply(BigDecimal.valueOf(refundPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal depositRefund = reservation.depositAmount().multiply(BigDecimal.valueOf(refundPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal refundAmount = rentRefund.add(depositRefund);
        jdbc.update("""
                UPDATE reservation
                SET status = 'CANCELLED', cancelled_at = ?, cancellation_reason = ?, refund_percent = ?
                WHERE reservation_id = ? AND customer_user_id = ?
                """, cancelledAt, reason == null || reason.isBlank() ? null : reason.trim(),
                refundPercent, reservationId, customerId);
        recordPayment(reservationId, "RENT_REFUND", "reservation:" + reservationId + ":cancel:rent-refund",
                rentRefund, reservation.currency());
        recordPayment(reservationId, "DEPOSIT_REFUND", "reservation:" + reservationId + ":cancel:deposit-refund",
                depositRefund, reservation.currency());
        return new CancellationResponse(reservationId, "CANCELLED", refundPercent, refundAmount, reservation.currency());
    }

    @Transactional
    public RentalLifecycleResponse handOver(long providerId, long reservationId) {
        List<RentalData> rows = findRental(reservationId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa reserva");
        }
        RentalData reservation = rows.getFirst();
        if (!"CONFIRMED".equals(reservation.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo se puede entregar una reserva confirmada");
        }
        if (providerAssignmentDoesNotMatch(providerId, reservation.businessId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El proveedor no está asignado a este negocio");
        }
        LocalDateTime handedOverAt = LocalDateTime.now();
        jdbc.update("INSERT INTO rental (reservation_item_id, handed_over_by_user_id, handed_over_at) VALUES (?, ?, ?)",
                reservation.itemId(), providerId, handedOverAt);
        jdbc.update("UPDATE reservation SET status = 'IN_RENTAL' WHERE reservation_id = ?", reservationId);
        return new RentalLifecycleResponse(reservationId, "IN_RENTAL", handedOverAt, null, null, null);
    }

    @Transactional
    public RentalLifecycleResponse returnRental(long providerId, long reservationId, String condition) {
        if (!List.of("GOOD", "PARTIAL_DAMAGE", "TOTAL_DAMAGE").contains(condition)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La condición debe ser GOOD, PARTIAL_DAMAGE o TOTAL_DAMAGE");
        }
        List<RentalData> rows = jdbc.query("""
                SELECT r.status, r.business_id, ri.reservation_item_id, re.rental_id,
                       re.handed_over_by_user_id, ri.deposit_snapshot, dr.currency
                FROM reservation r
                JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                JOIN rental re ON re.reservation_item_id = ri.reservation_item_id
                JOIN daily_rate dr ON dr.equipment_id = ri.equipment_id AND dr.rate_id = ri.rate_id
                WHERE r.reservation_id = ? AND re.returned_at IS NULL
                FOR UPDATE
                """, (rs, rowNum) -> new RentalData(rs.getString("status"), rs.getLong("business_id"),
                rs.getLong("reservation_item_id"), rs.getLong("rental_id"), rs.getLong("handed_over_by_user_id"),
                rs.getBigDecimal("deposit_snapshot"), rs.getString("currency")), reservationId);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe un alquiler activo para esa reserva");
        }
        RentalData rental = rows.getFirst();
        if (!"IN_RENTAL".equals(rental.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva no está en estado de alquiler");
        }
        if (providerAssignmentDoesNotMatch(providerId, rental.businessId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El proveedor no está asignado a este negocio");
        }
        LocalDateTime returnedAt = LocalDateTime.now();
        jdbc.update("UPDATE rental SET returned_to_user_id = ?, returned_at = ?, return_condition = ? WHERE rental_id = ?",
                providerId, returnedAt, condition, rental.rentalId());
        jdbc.update("UPDATE reservation SET status = 'COMPLETED' WHERE reservation_id = ?", reservationId);
        if (!"GOOD".equals(condition)) {
            jdbc.update("INSERT INTO damage_report (rental_id, reported_by_user_id, status) VALUES (?, ?, 'REPORTED')",
                    rental.rentalId(), rental.handedOverByUserId());
        } else {
            recordPayment(reservationId, "DEPOSIT_REFUND", "reservation:" + reservationId + ":deposit:return",
                    rental.depositAmount(), rental.currency());
        }
        return new RentalLifecycleResponse(reservationId, "COMPLETED", null, returnedAt, condition,
                "GOOD".equals(condition) ? "DEPOSIT_REFUNDED" : "DAMAGE_REVIEW_PENDING");
    }

    @Transactional
    public EvidenceResponse attachDamageEvidence(long providerId, long reservationId, String storageKey, String mediaType) {
        List<DamageReportData> reports = jdbc.query("""
                SELECT dr.damage_report_id, dr.reported_by_user_id, r.business_id
                FROM damage_report dr
                JOIN rental re ON re.rental_id = dr.rental_id
                JOIN reservation_item ri ON ri.reservation_item_id = re.reservation_item_id
                JOIN reservation r ON r.reservation_id = ri.reservation_id
                WHERE r.reservation_id = ? AND dr.status = 'REPORTED'
                ORDER BY dr.reported_at DESC
                LIMIT 1 FOR UPDATE
                """, (rs, rowNum) -> new DamageReportData(rs.getLong("damage_report_id"),
                rs.getLong("reported_by_user_id"), rs.getLong("business_id")), reservationId);
        if (reports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La reserva no tiene un reporte de daño pendiente");
        }
        DamageReportData report = reports.getFirst();
        if (report.reporterId() != providerId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el proveedor que entregó el equipo puede adjuntar la evidencia");
        }
        LocalDateTime uploadedAt = LocalDateTime.now();
        jdbc.update("""
                INSERT INTO damage_evidence (damage_report_id, uploaded_by_user_id, storage_key, media_type, uploaded_at)
                VALUES (?, ?, ?, ?, ?)
                """, report.reportId(), providerId, storageKey, mediaType, uploadedAt);
        Long evidenceId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return new EvidenceResponse(evidenceId, report.reportId(), mediaType, uploadedAt, providerId);
    }

    public List<PendingDamageReportResponse> listPendingDamageReports() {
        return jdbc.query("""
                SELECT dr.damage_report_id, r.reservation_id, ri.equipment_id, dr.reported_by_user_id,
                       u.display_name AS reporter_name, dr.reported_at, ri.deposit_snapshot, dr.status,
                       COUNT(de.evidence_id) AS evidence_count
                FROM damage_report dr
                JOIN rental re ON re.rental_id = dr.rental_id
                JOIN reservation_item ri ON ri.reservation_item_id = re.reservation_item_id
                JOIN reservation r ON r.reservation_id = ri.reservation_id
                JOIN app_user u ON u.user_id = dr.reported_by_user_id
                LEFT JOIN damage_evidence de ON de.damage_report_id = dr.damage_report_id
                WHERE dr.status = 'REPORTED'
                GROUP BY dr.damage_report_id, r.reservation_id, ri.equipment_id, dr.reported_by_user_id,
                         u.display_name, dr.reported_at, ri.deposit_snapshot, dr.status
                ORDER BY dr.reported_at
                """, (rs, rowNum) -> new PendingDamageReportResponse(rs.getLong("damage_report_id"),
                rs.getLong("reservation_id"), rs.getLong("equipment_id"), rs.getLong("reported_by_user_id"),
                rs.getString("reporter_name"), rs.getTimestamp("reported_at").toLocalDateTime(),
                rs.getBigDecimal("deposit_snapshot"), rs.getString("status"), rs.getLong("evidence_count")));
    }

    public List<EvidenceResponse> listDamageEvidence(long damageReportId) {
        return jdbc.query("""
                SELECT de.evidence_id, de.damage_report_id, de.media_type, de.uploaded_at, de.uploaded_by_user_id
                FROM damage_evidence de
                WHERE de.damage_report_id = ? ORDER BY de.uploaded_at
                """, (rs, rowNum) -> new EvidenceResponse(rs.getLong("evidence_id"),
                rs.getLong("damage_report_id"), rs.getString("media_type"),
                rs.getTimestamp("uploaded_at").toLocalDateTime(), rs.getLong("uploaded_by_user_id")), damageReportId);
    }

    public StoredEvidenceResponse findDamageEvidence(long damageReportId, long evidenceId) {
        List<StoredEvidenceResponse> files = jdbc.query("""
                SELECT storage_key, media_type FROM damage_evidence
                WHERE damage_report_id = ? AND evidence_id = ?
                """, (rs, rowNum) -> new StoredEvidenceResponse(rs.getString("storage_key"), rs.getString("media_type")),
                damageReportId, evidenceId);
        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa evidencia");
        }
        return files.getFirst();
    }

    @Transactional
    public DepositReviewResponse reviewDamage(long adminId, long damageReportId, String decision,
                                              BigDecimal assessedAmount, BigDecimal retainedAmount,
                                              String decisionNote) {
        List<DamageReviewData> reports = jdbc.query("""
                SELECT dr.status, r.reservation_id, ri.deposit_snapshot, drate.currency
                FROM damage_report dr
                JOIN rental re ON re.rental_id = dr.rental_id
                JOIN reservation_item ri ON ri.reservation_item_id = re.reservation_item_id
                JOIN reservation r ON r.reservation_id = ri.reservation_id
                JOIN daily_rate drate ON drate.equipment_id = ri.equipment_id AND drate.rate_id = ri.rate_id
                WHERE dr.damage_report_id = ?
                FOR UPDATE
                """, (rs, rowNum) -> new DamageReviewData(rs.getString("status"), rs.getLong("reservation_id"),
                rs.getBigDecimal("deposit_snapshot"), rs.getString("currency")), damageReportId);
        if (reports.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe ese reporte de daño");
        }
        DamageReviewData report = reports.getFirst();
        if (!"REPORTED".equals(report.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El reporte ya fue revisado");
        }
        Integer evidenceCount = jdbc.queryForObject("SELECT COUNT(*) FROM damage_evidence WHERE damage_report_id = ?",
                Integer.class, damageReportId);
        if (evidenceCount == null || evidenceCount == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Adjunta al menos una evidencia antes de revisar el daño");
        }
        DepositSettlement settlement;
        String reportStatus;
        try {
            if ("APPROVE".equals(decision)) {
                settlement = DepositSettlement.approve(report.depositAmount(), assessedAmount, retainedAmount);
                reportStatus = "APPROVED";
            } else if ("REJECT".equals(decision)) {
                if (retainedAmount.signum() != 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un reporte rechazado debe devolver el depósito completo");
                }
                settlement = DepositSettlement.reject(report.depositAmount(), assessedAmount);
                reportStatus = "REJECTED";
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La decisión debe ser APPROVE o REJECT");
            }
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
        jdbc.update("""
                UPDATE damage_report
                SET reviewed_by_user_id = ?, assessed_amount = ?, retained_amount = ?, status = ?, decision_note = ?
                WHERE damage_report_id = ?
                """, adminId, settlement.assessedAmount(), settlement.retainedAmount(), reportStatus,
                decisionNote == null || decisionNote.isBlank() ? null : decisionNote.trim(), damageReportId);
        recordPayment(report.reservationId(), "DEPOSIT_RETENTION",
                "reservation:" + report.reservationId() + ":deposit:retention", settlement.retainedAmount(), report.currency());
        recordPayment(report.reservationId(), "DEPOSIT_REFUND",
                "reservation:" + report.reservationId() + ":deposit:review-refund", settlement.refundAmount(), report.currency());
        return new DepositReviewResponse(damageReportId, reportStatus, settlement.assessedAmount(),
                settlement.retainedAmount(), settlement.refundAmount(), report.currency());
    }

    private void recordPayment(long reservationId, String operationType, String idempotencyKey,
                               BigDecimal amount, String currency) {
        if (amount.signum() <= 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO payment_operation
                  (reservation_id, operation_type, idempotency_key, amount, currency, status)
                VALUES (?, ?, ?, ?, ?, 'SUCCEEDED')
                """, reservationId, operationType, idempotencyKey, amount, currency);
    }

    private List<RentalData> findRental(long reservationId) {
        return jdbc.query("""
                SELECT r.status, r.business_id, ri.reservation_item_id
                FROM reservation r JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                WHERE r.reservation_id = ?
                FOR UPDATE
                """, (rs, rowNum) -> new RentalData(rs.getString("status"), rs.getLong("business_id"),
                rs.getLong("reservation_item_id"), null, null, null, null), reservationId);
    }

    private boolean providerAssignmentDoesNotMatch(long providerId, long businessId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM provider_assignment
                WHERE provider_user_id = ? AND business_id = ? AND ended_at IS NULL
                """, Integer.class, providerId, businessId);
        return count == null || count == 0;
    }

    private record Rate(long businessId, long rateId, BigDecimal dailyAmount, BigDecimal depositAmount, String currency) { }
    private record CancellationData(String status, LocalDateTime scheduledStartAt,
                                    BigDecimal rentalAmount, BigDecimal depositAmount, String currency) { }
    private record RentalData(String status, long businessId, long itemId, Long rentalId,
                              Long handedOverByUserId, BigDecimal depositAmount, String currency) { }
    private record DamageReportData(long reportId, long reporterId, long businessId) { }
    private record DamageReviewData(String status, long reservationId, BigDecimal depositAmount, String currency) { }

    public record ReservationResponse(long reservationId, long equipmentId, String status,
                                      LocalDate startDate, LocalDate returnDate, long calendarDays,
                                      BigDecimal dailyAmount, BigDecimal rentalAmount, BigDecimal depositAmount,
                                      String currency, Integer refundPercent, BigDecimal refundAmount) { }

    public record ProviderRentalResponse(long reservationId, long equipmentId, String status,
                                         LocalDate startDate, LocalDate returnDate, long calendarDays,
                                         BigDecimal rentalAmount, BigDecimal depositAmount, String currency,
                                         String customerName, String manufacturer, String modelName,
                                         String returnCondition, Long handedOverByUserId) { }

    public record CancellationResponse(long reservationId, String status, int refundPercent,
                                       BigDecimal refundAmount, String currency) { }

    public record RentalLifecycleResponse(long reservationId, String status,
                                          LocalDateTime handedOverAt, LocalDateTime returnedAt,
                                          String returnCondition, String depositAction) { }

    public record EvidenceResponse(long evidenceId, long damageReportId, String mediaType,
                                   LocalDateTime uploadedAt, long uploadedByUserId) { }

    public record PendingDamageReportResponse(long damageReportId, long reservationId, long equipmentId,
                                             long reportedByUserId, String reporterName, LocalDateTime reportedAt,
                                             BigDecimal depositAmount, String status, long evidenceCount) { }

    public record StoredEvidenceResponse(String storageKey, String mediaType) { }

    public record DepositReviewResponse(long damageReportId, String status, BigDecimal assessedAmount,
                                        BigDecimal retainedAmount, BigDecimal refundAmount, String currency) { }
}
