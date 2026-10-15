package com.machinext.reservation.application;

import com.machinext.catalog.domain.RentalPeriod;
import com.machinext.reservation.domain.CancellationPolicy;
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
        return new ReservationResponse(reservationId, equipmentId, "CONFIRMED", startDate, returnDate,
                period.calendarDaysInclusive(), rate.dailyAmount(), rentalAmount, rate.depositAmount(), rate.currency());
    }

    public List<ReservationResponse> listForCustomer(long customerId) {
        return jdbc.query("""
                SELECT r.reservation_id, ri.equipment_id, r.status, ri.start_date, ri.return_date,
                       DATEDIFF(ri.return_date, ri.start_date) + 1 AS calendar_days,
                       ri.daily_amount_snapshot, ri.rental_amount_snapshot, ri.deposit_snapshot, dr.currency
                FROM reservation r
                JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                JOIN daily_rate dr ON dr.equipment_id = ri.equipment_id AND dr.rate_id = ri.rate_id
                WHERE r.customer_user_id = ?
                ORDER BY r.created_at DESC, r.reservation_id DESC
                """, (rs, rowNum) -> new ReservationResponse(rs.getLong("reservation_id"),
                rs.getLong("equipment_id"), rs.getString("status"), rs.getDate("start_date").toLocalDate(),
                rs.getDate("return_date").toLocalDate(), rs.getLong("calendar_days"),
                rs.getBigDecimal("daily_amount_snapshot"), rs.getBigDecimal("rental_amount_snapshot"),
                rs.getBigDecimal("deposit_snapshot"), rs.getString("currency")), customerId);
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
        BigDecimal amountCharged = reservation.rentalAmount().add(reservation.depositAmount());
        BigDecimal refundAmount = amountCharged.multiply(BigDecimal.valueOf(refundPercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        jdbc.update("""
                UPDATE reservation
                SET status = 'CANCELLED', cancelled_at = ?, cancellation_reason = ?, refund_percent = ?
                WHERE reservation_id = ? AND customer_user_id = ?
                """, cancelledAt, reason == null || reason.isBlank() ? null : reason.trim(),
                refundPercent, reservationId, customerId);
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
                SELECT r.status, r.business_id, ri.reservation_item_id, re.rental_id
                FROM reservation r
                JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                JOIN rental re ON re.reservation_item_id = ri.reservation_item_id
                WHERE r.reservation_id = ? AND re.returned_at IS NULL
                FOR UPDATE
                """, (rs, rowNum) -> new RentalData(rs.getString("status"), rs.getLong("business_id"),
                rs.getLong("reservation_item_id"), rs.getLong("rental_id")), reservationId);
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
                    rental.rentalId(), providerId);
        }
        return new RentalLifecycleResponse(reservationId, "COMPLETED", null, returnedAt, condition,
                "GOOD".equals(condition) ? "DEPOSIT_REFUND_PENDING" : "DAMAGE_REVIEW_PENDING");
    }

    private List<RentalData> findRental(long reservationId) {
        return jdbc.query("""
                SELECT r.status, r.business_id, ri.reservation_item_id
                FROM reservation r JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                WHERE r.reservation_id = ?
                FOR UPDATE
                """, (rs, rowNum) -> new RentalData(rs.getString("status"), rs.getLong("business_id"),
                rs.getLong("reservation_item_id"), null), reservationId);
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
    private record RentalData(String status, long businessId, long itemId, Long rentalId) { }

    public record ReservationResponse(long reservationId, long equipmentId, String status,
                                      LocalDate startDate, LocalDate returnDate, long calendarDays,
                                      BigDecimal dailyAmount, BigDecimal rentalAmount, BigDecimal depositAmount,
                                      String currency) { }

    public record CancellationResponse(long reservationId, String status, int refundPercent,
                                       BigDecimal refundAmount, String currency) { }

    public record RentalLifecycleResponse(long reservationId, String status,
                                          LocalDateTime handedOverAt, LocalDateTime returnedAt,
                                          String returnCondition, String depositAction) { }
}
