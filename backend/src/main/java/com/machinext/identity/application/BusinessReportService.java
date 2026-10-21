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
                       (SELECT COUNT(*) FROM equipment_unit eu WHERE eu.business_id = b.business_id AND eu.operational_status = 'AVAILABLE'
                         AND NOT EXISTS (SELECT 1 FROM reservation_item ri JOIN reservation r ON r.reservation_id = ri.reservation_id
                           WHERE ri.equipment_id = eu.equipment_id AND r.status IN ('PENDING_PROVIDER', 'CONFIRMED', 'IN_RENTAL')
                             AND ri.start_date <= CURRENT_DATE AND ri.return_date >= CURRENT_DATE)) AS available_today,
                       (SELECT COUNT(*) FROM equipment_unit eu WHERE eu.business_id = b.business_id AND eu.operational_status = 'MAINTENANCE') AS maintenance_equipment,
                       (SELECT COUNT(*) FROM equipment_unit eu WHERE eu.business_id = b.business_id AND eu.operational_status = 'RETIRED') AS retired_equipment,
                       (SELECT COUNT(*) FROM reservation r WHERE r.business_id = b.business_id) AS reservation_count,
                       (SELECT COUNT(*) FROM reservation r WHERE r.business_id = b.business_id AND r.status = 'PENDING_PROVIDER') AS pending_provider_reservations,
                       (SELECT COUNT(*) FROM reservation r WHERE r.business_id = b.business_id AND r.status = 'CONFIRMED') AS confirmed_reservations,
                       (SELECT COUNT(*) FROM reservation r WHERE r.business_id = b.business_id AND r.status = 'IN_RENTAL') AS active_rentals,
                       (SELECT COUNT(*) FROM reservation r WHERE r.business_id = b.business_id AND r.status = 'COMPLETED') AS completed_rentals
                FROM business b
                JOIN business_admin ba ON ba.business_id = b.business_id AND ba.revoked_at IS NULL
                WHERE ba.user_id = ?
                ORDER BY b.legal_name
                """, (rs, row) -> new BusinessMetrics(rs.getLong("business_id"), rs.getString("legal_name"), rs.getString("status"),
                    rs.getLong("equipment_count"), rs.getLong("available_equipment"),
                    rs.getLong("available_today"),
                    rs.getLong("maintenance_equipment"), rs.getLong("retired_equipment"),
                    rs.getLong("reservation_count"), rs.getLong("pending_provider_reservations"), rs.getLong("confirmed_reservations"),
                    rs.getLong("active_rentals"), rs.getLong("completed_rentals")), userId);
        return businesses.stream().map(business -> new BusinessReport(business.businessId(), business.businessName(),
                business.businessStatus(), business.equipmentCount(), business.availableEquipment(),
                business.availableToday(), business.maintenanceEquipment(), business.retiredEquipment(), business.reservationCount(),
                business.pendingProviderReservations(), business.confirmedReservations(), business.activeRentals(), business.completedRentals(),
                listRentIncome(business.businessId()), listPendingDeposits(business.businessId()),
                listMostRentedEquipment(business.businessId()), listFrequentCustomers(business.businessId()))).toList();
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

    private List<EquipmentRentalCount> listMostRentedEquipment(long businessId) {
        return jdbc.query("""
                SELECT eu.equipment_id, em.manufacturer, em.model_name, COUNT(re.rental_id) AS rental_count
                FROM rental re
                JOIN reservation_item ri ON ri.reservation_item_id = re.reservation_item_id
                JOIN reservation r ON r.reservation_id = ri.reservation_id
                JOIN equipment_unit eu ON eu.equipment_id = ri.equipment_id
                JOIN equipment_model em ON em.model_id = eu.model_id
                WHERE r.business_id = ?
                GROUP BY eu.equipment_id, em.manufacturer, em.model_name
                ORDER BY rental_count DESC, em.manufacturer, em.model_name, eu.equipment_id
                LIMIT 10
                """, (rs, row) -> new EquipmentRentalCount(rs.getLong("equipment_id"),
                rs.getString("manufacturer"), rs.getString("model_name"), rs.getLong("rental_count")), businessId);
    }

    private List<FrequentCustomer> listFrequentCustomers(long businessId) {
        return jdbc.query("""
                SELECT u.display_name, COUNT(DISTINCT r.reservation_id) AS rental_count
                FROM reservation r
                JOIN app_user u ON u.user_id = r.customer_user_id
                JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                JOIN rental re ON re.reservation_item_id = ri.reservation_item_id
                WHERE r.business_id = ?
                GROUP BY u.user_id, u.display_name
                ORDER BY rental_count DESC, u.display_name
                LIMIT 10
                """, (rs, row) -> new FrequentCustomer(rs.getString("display_name"),
                rs.getLong("rental_count")), businessId);
    }

    private record BusinessMetrics(long businessId, String businessName, String businessStatus,
                                   long equipmentCount, long availableEquipment, long availableToday, long maintenanceEquipment,
                                   long retiredEquipment, long reservationCount, long pendingProviderReservations,
                                   long confirmedReservations, long activeRentals, long completedRentals) { }

    public record BusinessReport(long businessId, String businessName, String businessStatus,
                                 long equipmentCount, long availableEquipment, long availableToday, long maintenanceEquipment,
                                 long retiredEquipment, long reservationCount, long confirmedReservations,
                                 long activeRentals, long completedRentals, long pendingProviderReservations,
                                 List<RentIncomeByCurrency> rentIncomeByCurrency,
                                 List<DepositBalanceByCurrency> pendingDepositsByCurrency,
                                 List<EquipmentRentalCount> mostRentedEquipment,
                                 List<FrequentCustomer> frequentCustomers) { }

    public record RentIncomeByCurrency(String currency, BigDecimal rentCharged,
                                       BigDecimal rentRefunded, BigDecimal netRentIncome) { }

    public record DepositBalanceByCurrency(String currency, BigDecimal pendingDeposits) { }

    public record EquipmentRentalCount(long equipmentId, String manufacturer, String modelName,
                                       long rentalCount) { }

    public record FrequentCustomer(String displayName, long rentalCount) { }
}
