package com.machinext.identity.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "MACHI_DB_INTEGRATION", matches = "true")
class BusinessReportServiceIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BusinessReportService reports;

    @Test
    void businessReportIncludesRecentRentalHistoryForAssignedBusiness() {
        String suffix = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO business (legal_name, status) VALUES (?, 'ACTIVE')", "Report business " + suffix);
        long businessId = lastId();
        long managerId = createUser("manager-" + suffix + "@example.test", "BUSINESS_ADMIN");
        jdbc.update("INSERT INTO business_admin (business_id, user_id, assigned_by_user_id) VALUES (?, ?, ?)",
                businessId, managerId, managerId);
        long customerId = createUser("customer-" + suffix + "@example.test", "CLIENTE");
        String categoryName = "Report category " + suffix;
        jdbc.update("INSERT INTO category (name) VALUES (?)", categoryName);
        long categoryId = lastId();
        jdbc.update("INSERT INTO equipment_model (category_id, manufacturer, model_name) VALUES (?, ?, ?)",
                categoryId, "Report maker", "Report tool " + suffix);
        long modelId = lastId();
        jdbc.update("INSERT INTO equipment_unit (business_id, model_id, serial_number, operational_status) VALUES (?, ?, ?, 'AVAILABLE')",
                businessId, modelId, suffix);
        long equipmentId = lastId();
        jdbc.update("INSERT INTO daily_rate (equipment_id, daily_amount, deposit_amount, currency, valid_from) VALUES (?, ?, ?, 'COP', ?)",
                equipmentId, new BigDecimal("25000.00"), new BigDecimal("80000.00"), LocalDate.now().minusDays(1));
        long rateId = lastId();
        jdbc.update("INSERT INTO reservation (customer_user_id, business_id, status) VALUES (?, ?, 'COMPLETED')",
                customerId, businessId);
        long reservationId = lastId();
        LocalDate start = LocalDate.now().minusDays(3);
        jdbc.update("""
                INSERT INTO reservation_item (reservation_id, equipment_id, rate_id, start_date, return_date,
                    scheduled_start_at, daily_amount_snapshot, deposit_snapshot, rental_amount_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, reservationId, equipmentId, rateId, start, start.plusDays(2), start.atStartOfDay(),
                new BigDecimal("25000.00"), new BigDecimal("80000.00"), new BigDecimal("50000.00"));

        var result = reports.listForManager(managerId);

        assertEquals(1, result.size());
        assertEquals(businessId, result.getFirst().businessId());
        assertEquals(1, result.getFirst().rentalHistory().size());
        BusinessReportService.RentalHistoryEntry history = result.getFirst().rentalHistory().getFirst();
        assertEquals(reservationId, history.reservationId());
        assertEquals("COMPLETED", history.status());
        assertEquals("Report tool " + suffix, history.modelName());
        assertEquals(start, history.startDate());
        assertEquals(new BigDecimal("50000.00"), history.rentalAmount());
        assertNull(history.returnCondition());
    }

    private long createUser(String email, String role) {
        jdbc.update("INSERT INTO app_user (email, password_hash, display_name, account_status) VALUES (?, 'integration-test', ?, 'ACTIVE')",
                email, role + " report test");
        long userId = lastId();
        jdbc.update("INSERT INTO user_role (user_id, role_id) SELECT ?, role_id FROM role WHERE role_code = ?", userId, role);
        return userId;
    }

    private long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
}
