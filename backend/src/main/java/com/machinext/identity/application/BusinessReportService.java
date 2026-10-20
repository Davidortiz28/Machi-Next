package com.machinext.identity.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BusinessReportService {
    private final JdbcTemplate jdbc;

    public BusinessReportService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<BusinessReport> listForManager(long userId) {
        Integer activeManager = jdbc.queryForObject("""
                SELECT COUNT(*) FROM app_user u
                JOIN user_role ur ON ur.user_id = u.user_id
                JOIN role ro ON ro.role_id = ur.role_id AND ro.role_code = 'BUSINESS_ADMIN'
                JOIN business_admin ba ON ba.user_id = u.user_id AND ba.revoked_at IS NULL
                WHERE u.user_id = ? AND u.account_status = 'ACTIVE'
                """, Integer.class, userId);
        if (activeManager == null || activeManager == 0) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes negocios activos asignados para consultar reportes");
        }
        List<BusinessMetrics> businesses = jdbc.query("""
                SELECT b.business_id, b.legal_name, b.status,
                       (SELECT COUNT(*) FROM equipment_unit eu WHERE eu.business_id = b.business_id) AS equipment_count,
                       (SELECT COUNT(*) FROM equipment_unit eu WHERE eu.business_id = b.business_id AND eu.operational_status = 'AVAILABLE') AS available_equipment,
                       (SELECT COUNT(*) FROM equipment_unit eu WHERE eu.business_id = b.business_id AND eu.operational_status = 'MAINTENANCE') AS maintenance_equipment,
                       (SELECT COUNT(*) FROM equipment_unit eu WHERE eu.business_id = b.business_id AND eu.operational_status = 'RETIRED') AS retired_equipment,
                       (SELECT COUNT(*) FROM reservation r WHERE r.business_id = b.business_id) AS reservation_count,
                       (SELECT COUNT(*) FROM reservation r WHERE r.business_id = b.business_id AND r.status = 'CONFIRMED') AS confirmed_reservations,
                       (SELECT COUNT(*) FROM reservation r WHERE r.business_id = b.business_id AND r.status = 'IN_RENTAL') AS active_rentals,
                       (SELECT COUNT(*) FROM reservation r WHERE r.business_id = b.business_id AND r.status = 'COMPLETED') AS completed_rentals
                FROM business b
                JOIN business_admin ba ON ba.business_id = b.business_id AND ba.revoked_at IS NULL
                WHERE ba.user_id = ?
                ORDER BY b.legal_name
                """, (rs, row) -> new BusinessMetrics(rs.getLong("business_id"), rs.getString("legal_name"), rs.getString("status"),
                    rs.getLong("equipment_count"), rs.getLong("available_equipment"),
                    rs.getLong("maintenance_equipment"), rs.getLong("retired_equipment"),
                    rs.getLong("reservation_count"), rs.getLong("confirmed_reservations"),
                    rs.getLong("active_rentals"), rs.getLong("completed_rentals")), userId);
        return businesses.stream().map(business -> new BusinessReport(business.businessId(), business.businessName(),
                business.businessStatus(), business.equipmentCount(), business.availableEquipment(),
                business.maintenanceEquipment(), business.retiredEquipment(), business.reservationCount(),
                business.confirmedReservations(), business.activeRentals(), business.completedRentals(),
                listRentIncome(business.businessId()), listPendingDeposits(business.businessId()))).toList();
    }

    private List<RentIncomeByCurrency> listRentIncome(long businessId) {
        return jdbc.query("""
                SELECT po.currency,
                       SUM(CASE WHEN po.operation_type = 'RENT_CHARGE' THEN po.amount ELSE 0 END) AS rent_charged,
                       SUM(CASE WHEN po.operation_type = 'RENT_REFUND' THEN po.amount ELSE 0 END) AS rent_refunded
                FROM payment_operation po JOIN reservation r ON r.reservation_id = po.reservation_id
                WHERE r.business_id = ? AND po.operation_type IN ('RENT_CHARGE', 'RENT_REFUND')
                  AND po.status = 'SUCCEEDED'
                GROUP BY po.currency ORDER BY po.currency
                """, (rs, row) -> {
            BigDecimal charged = rs.getBigDecimal("rent_charged");
            BigDecimal refunded = rs.getBigDecimal("rent_refunded");
            return new RentIncomeByCurrency(rs.getString("currency"), charged, refunded, charged.subtract(refunded));
        }, businessId);
    }

    private List<DepositBalanceByCurrency> listPendingDeposits(long businessId) {
        return jdbc.query("""
                SELECT dr.currency, SUM(ri.deposit_snapshot) AS pending_deposits
                FROM reservation r JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                JOIN daily_rate dr ON dr.equipment_id = ri.equipment_id AND dr.rate_id = ri.rate_id
                WHERE r.business_id = ? AND (r.status = 'IN_RENTAL' OR
                  (r.status = 'COMPLETED' AND EXISTS (
                    SELECT 1 FROM damage_report d JOIN rental re ON re.rental_id = d.rental_id
                    WHERE re.reservation_item_id = ri.reservation_item_id AND d.status = 'REPORTED'
                  )))
                GROUP BY dr.currency ORDER BY dr.currency
                """, (rs, row) -> new DepositBalanceByCurrency(rs.getString("currency"),
                rs.getBigDecimal("pending_deposits")), businessId);
    }

    private record BusinessMetrics(long businessId, String businessName, String businessStatus,
                                   long equipmentCount, long availableEquipment, long maintenanceEquipment,
                                   long retiredEquipment, long reservationCount, long confirmedReservations,
                                   long activeRentals, long completedRentals) { }

    public record BusinessReport(long businessId, String businessName, String businessStatus,
                                 long equipmentCount, long availableEquipment, long maintenanceEquipment,
                                 long retiredEquipment, long reservationCount, long confirmedReservations,
                                 long activeRentals, long completedRentals,
                                 List<RentIncomeByCurrency> rentIncomeByCurrency,
                                 List<DepositBalanceByCurrency> pendingDepositsByCurrency) { }

    public record RentIncomeByCurrency(String currency, BigDecimal rentCharged,
                                       BigDecimal rentRefunded, BigDecimal netRentIncome) { }

    public record DepositBalanceByCurrency(String currency, BigDecimal pendingDeposits) { }
}
