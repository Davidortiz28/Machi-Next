package com.machinext.reservation.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "MACHI_DB_INTEGRATION", matches = "true")
class ReservationApprovalIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReservationService reservations;

    @Test
    void acceptedRequestIssuesReceiptAndEnablesHandoverState() {
        Fixture fixture = fixture();
        LocalDate start = LocalDate.now().plusDays(10);
        ReservationService.ReservationResponse request = reservations.create(fixture.customerId(), fixture.equipmentId(),
                start, start.plusDays(2));

        assertEquals("PENDING_PROVIDER", request.status());
        ReservationService.ReservationDecisionResponse result = reservations.decide(fixture.providerId(),
                request.reservationId(), "ACCEPT");

        assertEquals("CONFIRMED", result.status());
        assertEquals("ISSUED", jdbc.queryForObject("SELECT status FROM invoice WHERE reservation_id = ?",
                String.class, request.reservationId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM reservation_item ri JOIN reservation r ON r.reservation_id = ri.reservation_id WHERE ri.equipment_id = ? AND r.status = 'CONFIRMED'",
                Integer.class, fixture.equipmentId()));
    }

    @Test
    void rejectedRequestReturnsAllSimulatedChargesAndCancelsReceipt() {
        Fixture fixture = fixture();
        LocalDate start = LocalDate.now().plusDays(15);
        ReservationService.ReservationResponse request = reservations.create(fixture.customerId(), fixture.equipmentId(),
                start, start.plusDays(1));

        ReservationService.ReservationDecisionResponse result = reservations.decide(fixture.providerId(),
                request.reservationId(), "REJECT");

        assertEquals("REJECTED", result.status());
        assertEquals(request.rentalAmount().add(request.depositAmount()), result.refundAmount());
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM invoice WHERE reservation_id = ?",
                String.class, request.reservationId()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM payment_operation WHERE reservation_id = ? AND operation_type IN ('RENT_REFUND', 'DEPOSIT_REFUND')",
                Integer.class, request.reservationId()));
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO business (legal_name, status) VALUES (?, 'ACTIVE')", "Integration business " + suffix);
        long businessId = lastId();
        long providerId = createUser("provider-" + suffix + "@example.test", "PROVEEDOR");
        long customerId = createUser("customer-" + suffix + "@example.test", "CLIENTE");
        jdbc.update("INSERT INTO provider_assignment (provider_user_id, business_id, started_at) VALUES (?, ?, CURRENT_TIMESTAMP(6))",
                providerId, businessId);
        String category = "Integration category " + suffix;
        jdbc.update("INSERT INTO category (name) VALUES (?)", category);
        long categoryId = lastId();
        jdbc.update("INSERT INTO equipment_model (category_id, manufacturer, model_name, description) VALUES (?, ?, ?, NULL)",
                categoryId, "Test manufacturer", "Test model " + suffix);
        long modelId = lastId();
        jdbc.update("INSERT INTO equipment_unit (business_id, model_id, serial_number, operational_status) VALUES (?, ?, ?, 'AVAILABLE')",
                businessId, modelId, suffix);
        long equipmentId = lastId();
        jdbc.update("INSERT INTO daily_rate (equipment_id, daily_amount, deposit_amount, currency, valid_from) VALUES (?, ?, ?, 'COP', ?)",
                equipmentId, new BigDecimal("40000.00"), new BigDecimal("90000.00"), LocalDate.now().minusDays(1));
        return new Fixture(providerId, customerId, equipmentId);
    }

    private long createUser(String email, String role) {
        jdbc.update("INSERT INTO app_user (email, password_hash, display_name, account_status) VALUES (?, 'integration-test', ?, 'ACTIVE')",
                email, role + " integration");
        long userId = lastId();
        jdbc.update("INSERT INTO user_role (user_id, role_id) SELECT ?, role_id FROM role WHERE role_code = ?", userId, role);
        return userId;
    }

    private long lastId() { return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }

    private record Fixture(long providerId, long customerId, long equipmentId) { }
}
