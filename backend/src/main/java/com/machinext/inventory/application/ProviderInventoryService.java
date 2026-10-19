package com.machinext.inventory.application;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Service
public class ProviderInventoryService {
    private final JdbcTemplate jdbc;

    public ProviderInventoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<InventoryItem> listForProvider(long providerUserId) {
        long businessId = currentBusinessId(providerUserId, false);
        return jdbc.query("""
                SELECT eu.equipment_id, eu.business_id, b.legal_name, c.name AS category_name,
                       em.manufacturer, em.model_name, em.description, eu.serial_number, eu.operational_status,
                       dr.daily_amount, dr.deposit_amount, dr.currency, dr.valid_from, dr.valid_to
                FROM equipment_unit eu
                JOIN business b ON b.business_id = eu.business_id
                JOIN equipment_model em ON em.model_id = eu.model_id
                JOIN category c ON c.category_id = em.category_id
                LEFT JOIN daily_rate dr ON dr.rate_id = (
                    SELECT r.rate_id FROM daily_rate r WHERE r.equipment_id = eu.equipment_id
                    ORDER BY r.valid_from DESC, r.rate_id DESC LIMIT 1
                )
                WHERE eu.business_id = ?
                ORDER BY c.name, em.model_name, eu.serial_number
                """, (rs, row) -> new InventoryItem(rs.getLong("equipment_id"), rs.getLong("business_id"),
                rs.getString("legal_name"), rs.getString("category_name"), rs.getString("manufacturer"),
                rs.getString("model_name"), rs.getString("description"), rs.getString("serial_number"),
                rs.getString("operational_status"), rs.getBigDecimal("daily_amount"), rs.getBigDecimal("deposit_amount"),
                rs.getString("currency"), localDate(rs.getDate("valid_from")), localDate(rs.getDate("valid_to"))), businessId);
    }

    @Transactional
    public InventoryItem create(long providerUserId, CreateItemCommand command) {
        long businessId = currentBusinessId(providerUserId, true);
        if (command.validTo() != null && !command.validTo().isAfter(command.validFrom())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La tarifa debe vencer después de su fecha de inicio");
        }
        try {
            jdbc.update("INSERT INTO category (name) VALUES (?) ON DUPLICATE KEY UPDATE category_id = LAST_INSERT_ID(category_id)",
                    command.categoryName().trim());
            Long categoryId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("""
                    INSERT INTO equipment_model (category_id, manufacturer, model_name, description)
                    VALUES (?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE model_id = LAST_INSERT_ID(model_id)
                    """, categoryId, command.manufacturer().trim(), command.modelName().trim(), normalizeDescription(command.description()));
            Long modelId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("""
                    INSERT INTO equipment_unit (business_id, model_id, serial_number, operational_status)
                    VALUES (?, ?, ?, 'AVAILABLE')
                    """, businessId, modelId, command.serialNumber().trim());
            Long equipmentId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            jdbc.update("""
                    INSERT INTO daily_rate (equipment_id, daily_amount, deposit_amount, currency, valid_from, valid_to)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, equipmentId, command.dailyAmount(), command.depositAmount(),
                    command.currency() == null ? "COP" : command.currency(), Date.valueOf(command.validFrom()),
                    command.validTo() == null ? null : Date.valueOf(command.validTo()));
            return listForProvider(providerUserId).stream().filter(item -> item.equipmentId().equals(equipmentId))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No se pudo recuperar el equipo recién creado"));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un equipo con ese número de serie en tu negocio");
        }
    }

    @Transactional
    public InventoryItem updateStatus(long providerUserId, long equipmentId, String status) {
        long businessId = currentBusinessId(providerUserId, true);
        int updated = jdbc.update("UPDATE equipment_unit SET operational_status = ? WHERE equipment_id = ? AND business_id = ?",
                status, equipmentId, businessId);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipo no encontrado en tu negocio");
        return listForProvider(providerUserId).stream().filter(item -> item.equipmentId() == equipmentId)
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Equipo no encontrado"));
    }

    private long currentBusinessId(long providerUserId, boolean lock) {
        String sql = """
                SELECT a.business_id, b.status AS business_status, u.account_status
                FROM provider_assignment a
                JOIN business b ON b.business_id = a.business_id
                JOIN app_user u ON u.user_id = a.provider_user_id
                JOIN user_role ur ON ur.user_id = u.user_id
                JOIN role r ON r.role_id = ur.role_id AND r.role_code = 'PROVEEDOR'
                WHERE a.provider_user_id = ? AND a.ended_at IS NULL AND u.account_status = 'ACTIVE'
                """ + (lock ? " FOR UPDATE" : "");
        List<Assignment> assignments = jdbc.query(sql, (rs, row) -> new Assignment(rs.getLong("business_id"),
                rs.getString("business_status")), providerUserId);
        if (assignments.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes un negocio activo asignado");
        Assignment assignment = assignments.getFirst();
        if (!"ACTIVE".equals(assignment.businessStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El negocio asignado no está activo");
        }
        return assignment.businessId();
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    private LocalDate localDate(Date date) { return date == null ? null : date.toLocalDate(); }

    private record Assignment(long businessId, String businessStatus) { }

    public record CreateItemCommand(String categoryName, String manufacturer, String modelName, String description,
                                    String serialNumber, BigDecimal dailyAmount, BigDecimal depositAmount,
                                    String currency, LocalDate validFrom, LocalDate validTo) { }

    public record InventoryItem(Long equipmentId, Long businessId, String businessName, String categoryName,
                                String manufacturer, String modelName, String description, String serialNumber,
                                String operationalStatus, BigDecimal dailyAmount, BigDecimal depositAmount,
                                String currency, LocalDate validFrom, LocalDate validTo) { }
}
