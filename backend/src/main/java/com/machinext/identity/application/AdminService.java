package com.machinext.identity.application;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class AdminService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public AdminService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public List<BusinessSummary> listBusinesses() {
        return jdbc.query("SELECT business_id, legal_name, status FROM business ORDER BY legal_name",
                (rs, row) -> new BusinessSummary(rs.getLong("business_id"), rs.getString("legal_name"), rs.getString("status")));
    }

    public List<ProviderAccountSummary> listProviders() {
        return jdbc.query("""
                SELECT u.user_id, u.email, u.display_name, u.account_status,
                       a.business_id, b.legal_name AS business_name, b.status AS business_status
                FROM app_user u
                JOIN user_role ur ON ur.user_id = u.user_id
                JOIN role r ON r.role_id = ur.role_id AND r.role_code = 'PROVEEDOR'
                LEFT JOIN provider_assignment a ON a.provider_user_id = u.user_id AND a.ended_at IS NULL
                LEFT JOIN business b ON b.business_id = a.business_id
                ORDER BY u.display_name, u.user_id
                """, (rs, row) -> new ProviderAccountSummary(rs.getLong("user_id"), rs.getString("email"),
                rs.getString("display_name"), rs.getString("account_status"),
                rs.getObject("business_id") == null ? null : ((Number) rs.getObject("business_id")).longValue(),
                rs.getString("business_name"), rs.getString("business_status")));
    }

    public List<UserAccountSummary> listUsers() {
        return jdbc.query("""
                SELECT u.user_id, u.email, u.display_name, u.account_status,
                       GROUP_CONCAT(DISTINCT r.role_code ORDER BY r.role_code SEPARATOR ',') AS roles,
                       u.created_at
                FROM app_user u
                LEFT JOIN user_role ur ON ur.user_id = u.user_id
                LEFT JOIN role r ON r.role_id = ur.role_id
                GROUP BY u.user_id, u.email, u.display_name, u.account_status, u.created_at
                ORDER BY u.created_at DESC, u.user_id DESC
                """, (rs, row) -> new UserAccountSummary(rs.getLong("user_id"), rs.getString("email"),
                rs.getString("display_name"), rs.getString("account_status"), rs.getString("roles"),
                rs.getTimestamp("created_at").toInstant()));
    }

    @Transactional
    public UserAccountSummary updateUserStatus(long adminId, long userId, String status) {
        if (!List.of("ACTIVE", "INACTIVE", "LOCKED").contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El estado debe ser ACTIVE, INACTIVE o LOCKED");
        }
        if (adminId == userId && !"ACTIVE".equals(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No puedes desactivar tu propia cuenta administrativa");
        }
        List<String> roles = jdbc.query("""
                SELECT r.role_code FROM user_role ur JOIN role r ON r.role_id = ur.role_id
                WHERE ur.user_id = ?
                """, (rs, row) -> rs.getString(1), userId);
        if (roles.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa cuenta");
        if (roles.contains("ADMIN") && !"ACTIVE".equals(status)) {
            jdbc.query("""
                    SELECT u.user_id FROM app_user u
                    JOIN user_role ur ON ur.user_id = u.user_id
                    JOIN role r ON r.role_id = ur.role_id AND r.role_code = 'ADMIN'
                    WHERE u.account_status = 'ACTIVE' FOR UPDATE
                    """, (rs, row) -> rs.getLong(1));
            Integer activeAdmins = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM app_user u
                    JOIN user_role ur ON ur.user_id = u.user_id
                    JOIN role r ON r.role_id = ur.role_id AND r.role_code = 'ADMIN'
                    WHERE u.account_status = 'ACTIVE' AND u.user_id <> ?
                    """, Integer.class, userId);
            if (activeAdmins == null || activeAdmins == 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede desactivar el Ãºltimo administrador activo");
            }
        }
        int changed = jdbc.update("UPDATE app_user SET account_status = ? WHERE user_id = ?", status, userId);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa cuenta");
        return listUsers().stream().filter(user -> user.userId() == userId).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe esa cuenta"));
    }

    public List<AdminRentalSummary> listRentalHistory() {
        return jdbc.query("""
                SELECT r.reservation_id, r.status, b.legal_name AS business_name,
                       cu.display_name AS customer_name, eu.equipment_id, em.manufacturer, em.model_name,
                       ri.start_date, ri.return_date, ri.rental_amount_snapshot, ri.deposit_snapshot,
                       dr.currency, re.handed_over_at, re.returned_at, re.return_condition,
                       hu.display_name AS handed_over_by, ru.display_name AS returned_to
                FROM reservation r
                JOIN business b ON b.business_id = r.business_id
                JOIN app_user cu ON cu.user_id = r.customer_user_id
                JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                JOIN equipment_unit eu ON eu.equipment_id = ri.equipment_id
                JOIN equipment_model em ON em.model_id = eu.model_id
                JOIN daily_rate dr ON dr.equipment_id = ri.equipment_id AND dr.rate_id = ri.rate_id
                LEFT JOIN rental re ON re.reservation_item_id = ri.reservation_item_id
                LEFT JOIN app_user hu ON hu.user_id = re.handed_over_by_user_id
                LEFT JOIN app_user ru ON ru.user_id = re.returned_to_user_id
                ORDER BY r.created_at DESC, r.reservation_id DESC
                """, (rs, row) -> new AdminRentalSummary(rs.getLong("reservation_id"), rs.getString("status"),
                rs.getString("business_name"), rs.getString("customer_name"), rs.getLong("equipment_id"),
                rs.getString("manufacturer"), rs.getString("model_name"), rs.getDate("start_date").toLocalDate(),
                rs.getDate("return_date").toLocalDate(), rs.getBigDecimal("rental_amount_snapshot"),
                rs.getBigDecimal("deposit_snapshot"), rs.getString("currency"),
                rs.getTimestamp("handed_over_at") == null ? null : rs.getTimestamp("handed_over_at").toLocalDateTime(),
                rs.getTimestamp("returned_at") == null ? null : rs.getTimestamp("returned_at").toLocalDateTime(),
                rs.getString("return_condition"), rs.getString("handed_over_by"), rs.getString("returned_to")));
    }

    public AdminOverview reportOverview() {
        InventoryOverview inventory = jdbc.queryForObject("""
                SELECT COUNT(*) AS total_equipment,
                       SUM(eu.operational_status = 'AVAILABLE') AS operational_available,
                       SUM(eu.operational_status = 'MAINTENANCE') AS in_maintenance,
                       SUM(eu.operational_status = 'RETIRED') AS retired,
                       SUM(eu.operational_status = 'AVAILABLE' AND NOT EXISTS (
                         SELECT 1 FROM reservation_item ri JOIN reservation r ON r.reservation_id = ri.reservation_id
                         WHERE ri.equipment_id = eu.equipment_id
                           AND r.status IN ('PENDING_PROVIDER', 'CONFIRMED', 'IN_RENTAL')
                           AND ri.start_date <= CURRENT_DATE AND ri.return_date >= CURRENT_DATE
                       )) AS available_today
                FROM equipment_unit eu
                """, (rs, row) -> new InventoryOverview(rs.getLong("total_equipment"),
                rs.getLong("operational_available"), rs.getLong("in_maintenance"), rs.getLong("retired"),
                rs.getLong("available_today")));
        List<IncomeSummary> income = jdbc.query("""
                SELECT currency,
                       SUM(CASE WHEN operation_type = 'RENT_CHARGE' THEN amount ELSE 0 END) AS charged,
                       SUM(CASE WHEN operation_type = 'RENT_REFUND' THEN amount ELSE 0 END) AS refunded
                FROM payment_operation WHERE status = 'SUCCEEDED'
                  AND operation_type IN ('RENT_CHARGE', 'RENT_REFUND')
                GROUP BY currency ORDER BY currency
                """, (rs, row) -> {
            java.math.BigDecimal charged = rs.getBigDecimal("charged");
            java.math.BigDecimal refunded = rs.getBigDecimal("refunded");
            return new IncomeSummary(rs.getString("currency"), charged, refunded, charged.subtract(refunded));
        });
        List<PopularEquipment> popular = jdbc.query("""
                SELECT em.manufacturer, em.model_name, COUNT(re.rental_id) AS rentals
                FROM rental re JOIN reservation_item ri ON ri.reservation_item_id = re.reservation_item_id
                JOIN equipment_unit eu ON eu.equipment_id = ri.equipment_id
                JOIN equipment_model em ON em.model_id = eu.model_id
                GROUP BY em.model_id, em.manufacturer, em.model_name
                ORDER BY rentals DESC, em.manufacturer, em.model_name LIMIT 10
                """, (rs, row) -> new PopularEquipment(rs.getString("manufacturer"), rs.getString("model_name"), rs.getLong("rentals")));
        List<FrequentCustomer> customers = jdbc.query("""
                SELECT u.display_name, COUNT(DISTINCT r.reservation_id) AS rentals
                FROM reservation r JOIN app_user u ON u.user_id = r.customer_user_id
                JOIN reservation_item ri ON ri.reservation_id = r.reservation_id
                JOIN rental re ON re.reservation_item_id = ri.reservation_item_id
                GROUP BY u.user_id, u.display_name
                ORDER BY rentals DESC, u.display_name LIMIT 10
                """, (rs, row) -> new FrequentCustomer(rs.getString("display_name"), rs.getLong("rentals")));
        return new AdminOverview(inventory, income, popular, customers,
                countByReservationStatus(), jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Long.class));
    }

    private List<ReservationStatusCount> countByReservationStatus() {
        return jdbc.query("SELECT status, COUNT(*) AS total FROM reservation GROUP BY status ORDER BY status",
                (rs, row) -> new ReservationStatusCount(rs.getString("status"), rs.getLong("total")));
    }

    @Transactional
    public ProviderSummary createProvider(String email, String password, String displayName, Long businessId) {
        BusinessSummary business = jdbc.query("SELECT business_id, legal_name, status FROM business WHERE business_id = ?",
                (rs, row) -> new BusinessSummary(rs.getLong("business_id"), rs.getString("legal_name"), rs.getString("status")), businessId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio no encontrado"));
        if (!"ACTIVE".equals(business.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El proveedor debe asignarse a un negocio activo");
        }
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        String normalizedName = displayName.trim();
        try {
            jdbc.update("INSERT INTO app_user (email, password_hash, display_name, account_status) VALUES (?, ?, ?, 'ACTIVE')",
                    normalizedEmail, passwordEncoder.encode(password), normalizedName);
            Long userId = jdbc.queryForObject("SELECT user_id FROM app_user WHERE email = ?", Long.class, normalizedEmail);
            int roleRows = jdbc.update("INSERT INTO user_role (user_id, role_id) SELECT ?, role_id FROM role WHERE role_code = 'PROVEEDOR'", userId);
            if (roleRows != 1) throw new IllegalStateException("Falta configurar el rol PROVEEDOR");
            jdbc.update("INSERT INTO provider_assignment (provider_user_id, business_id, started_at) VALUES (?, ?, ?)",
                    userId, businessId, Instant.now());
            return new ProviderSummary(userId, normalizedEmail, normalizedName, business, "ACTIVE");
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }
    }

    @Transactional
    public BusinessManagerSummary createBusinessManager(String email, String password, String displayName,
                                                        Long businessId, Long assignedByUserId) {
        BusinessSummary business = jdbc.query("SELECT business_id, legal_name, status FROM business WHERE business_id = ?",
                (rs, row) -> new BusinessSummary(rs.getLong("business_id"), rs.getString("legal_name"), rs.getString("status")), businessId)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio no encontrado"));
        if (!"ACTIVE".equals(business.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El representante debe asignarse a un negocio activo");
        }
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        String normalizedName = displayName.trim();
        try {
            jdbc.update("INSERT INTO app_user (email, password_hash, display_name, account_status) VALUES (?, ?, ?, 'ACTIVE')",
                    normalizedEmail, passwordEncoder.encode(password), normalizedName);
            Long userId = jdbc.queryForObject("SELECT user_id FROM app_user WHERE email = ?", Long.class, normalizedEmail);
            int roleRows = jdbc.update("INSERT INTO user_role (user_id, role_id) SELECT ?, role_id FROM role WHERE role_code = 'BUSINESS_ADMIN'", userId);
            if (roleRows != 1) throw new IllegalStateException("Falta configurar el rol BUSINESS_ADMIN");
            jdbc.update("INSERT INTO business_admin (business_id, user_id, assigned_by_user_id) VALUES (?, ?, ?)",
                    businessId, userId, assignedByUserId);
            return new BusinessManagerSummary(userId, normalizedEmail, normalizedName, business);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }
    }

    @Transactional
    public void revokeBusinessManager(long userId, long businessId) {
        int updated = jdbc.update("""
                UPDATE business_admin SET revoked_at = CURRENT_TIMESTAMP(6)
                WHERE user_id = ? AND business_id = ? AND revoked_at IS NULL
                """, userId, businessId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Representante activo no encontrado para ese negocio");
    }

    public record BusinessSummary(Long businessId, String legalName, String status) { }
    public record ProviderSummary(Long userId, String email, String displayName, BusinessSummary business, String status) { }
    public record ProviderAccountSummary(Long userId, String email, String displayName, String accountStatus,
                                         Long businessId, String businessName, String businessStatus) { }
    public record BusinessManagerSummary(Long userId, String email, String displayName, BusinessSummary business) { }
    public record UserAccountSummary(long userId, String email, String displayName, String accountStatus,
                                     String roles, java.time.Instant createdAt) { }
    public record AdminRentalSummary(long reservationId, String status, String businessName, String customerName,
                                     long equipmentId, String manufacturer, String modelName,
                                     java.time.LocalDate startDate, java.time.LocalDate returnDate,
                                     java.math.BigDecimal rentalAmount, java.math.BigDecimal depositAmount,
                                     String currency, java.time.LocalDateTime handedOverAt,
                                     java.time.LocalDateTime returnedAt, String returnCondition,
                                     String handedOverBy, String returnedTo) { }
    public record AdminOverview(InventoryOverview inventory, List<IncomeSummary> rentIncomeByCurrency,
                                List<PopularEquipment> mostRentedEquipment, List<FrequentCustomer> frequentCustomers,
                                List<ReservationStatusCount> reservationsByStatus, long registeredUsers) { }
    public record InventoryOverview(long totalEquipment, long operationalAvailable, long inMaintenance,
                                    long retired, long availableToday) { }
    public record IncomeSummary(String currency, java.math.BigDecimal rentCharged,
                                java.math.BigDecimal rentRefunded, java.math.BigDecimal netRentIncome) { }
    public record PopularEquipment(String manufacturer, String modelName, long rentalCount) { }
    public record FrequentCustomer(String displayName, long rentalCount) { }
    public record ReservationStatusCount(String status, long total) { }
}
