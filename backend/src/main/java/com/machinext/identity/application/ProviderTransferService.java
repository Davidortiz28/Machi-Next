package com.machinext.identity.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.List;

@Service
public class ProviderTransferService {
    private final JdbcTemplate jdbc;

    public ProviderTransferService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public TransferResponse request(long providerUserId, long toBusinessId) {
        List<ProviderAssignment> assignments = jdbc.query("""
                SELECT a.business_id, u.account_status
                FROM provider_assignment a
                JOIN app_user u ON u.user_id = a.provider_user_id
                JOIN user_role ur ON ur.user_id = u.user_id
                JOIN role r ON r.role_id = ur.role_id AND r.role_code = 'PROVEEDOR'
                WHERE a.provider_user_id = ? AND a.ended_at IS NULL
                FOR UPDATE
                """, (rs, row) -> new ProviderAssignment(rs.getLong("business_id"), rs.getString("account_status")), providerUserId);
        if (assignments.isEmpty() || !"ACTIVE".equals(assignments.getFirst().status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El proveedor no tiene una asignación activa");
        }
        long fromBusinessId = assignments.getFirst().businessId();
        if (fromBusinessId == toBusinessId) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El proveedor ya está asignado a ese negocio");
        }
        List<Business> destinations = jdbc.query("SELECT business_id, legal_name FROM business WHERE business_id = ? AND status = 'ACTIVE'",
                (rs, row) -> new Business(rs.getLong("business_id"), rs.getString("legal_name")), toBusinessId);
        if (destinations.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Negocio de destino no encontrado o inactivo");
        Integer pending = jdbc.queryForObject("SELECT COUNT(*) FROM provider_transfer WHERE provider_user_id = ? AND status IN ('PENDING', 'WAITING')",
                Integer.class, providerUserId);
        if (pending != null && pending > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "El proveedor ya tiene una transferencia pendiente");
        jdbc.update("""
                INSERT INTO provider_transfer (provider_user_id, from_business_id, to_business_id, requested_at, status)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP(6), 'PENDING')
                """, providerUserId, fromBusinessId, toBusinessId);
        Long transferId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return getTransfer(transferId);
    }

    public List<TransferResponse> list() {
        return jdbc.query(selectTransferSql() + " ORDER BY t.requested_at DESC",
                (rs, row) -> mapTransfer(rs));
    }

    @Transactional
    public TransferResponse decide(long transferId, long adminUserId, String side, String decision) {
        requireActiveAdmin(adminUserId);
        List<TransferRow> rows = jdbc.query("SELECT status, from_business_decision, to_business_decision FROM provider_transfer WHERE transfer_id = ? FOR UPDATE",
                (rs, row) -> new TransferRow(rs.getString("status"), rs.getString("from_business_decision"), rs.getString("to_business_decision")), transferId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud de transferencia no encontrada");
        TransferRow transfer = rows.getFirst();
        if (!"PENDING".equals(transfer.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "La solicitud ya fue resuelta");
        String priorDecision = "SOURCE".equals(side) ? transfer.fromDecision() : transfer.toDecision();
        if (priorDecision != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Este negocio ya registró su decisión");

        if ("SOURCE".equals(side)) {
            jdbc.update("""
                    UPDATE provider_transfer SET from_business_decision = ?, from_business_decided_at = CURRENT_TIMESTAMP(6),
                        from_business_decided_by_user_id = ? WHERE transfer_id = ?
                    """, decision, adminUserId, transferId);
        } else {
            jdbc.update("""
                    UPDATE provider_transfer SET to_business_decision = ?, to_business_decided_at = CURRENT_TIMESTAMP(6),
                        to_business_decided_by_user_id = ? WHERE transfer_id = ?
                    """, decision, adminUserId, transferId);
        }
        String nextStatus = "REJECTED".equals(decision) || "REJECTED".equals(priorDecision) ? "REJECTED"
                : ("APPROVED".equals("SOURCE".equals(side) ? transfer.toDecision() : transfer.fromDecision())
                    ? "WAITING" : null);
        if ("REJECTED".equals(nextStatus)) {
            jdbc.update("UPDATE provider_transfer SET status = ?, resolved_at = CURRENT_TIMESTAMP(6), resolved_by_user_id = ? WHERE transfer_id = ?",
                    nextStatus, adminUserId, transferId);
        } else if ("WAITING".equals(nextStatus)) {
            jdbc.update("""
                    UPDATE provider_transfer
                    SET status = 'WAITING', approved_at = CURRENT_TIMESTAMP(6),
                        effective_at = DATE_ADD(CURRENT_TIMESTAMP(6), INTERVAL 3 DAY),
                        resolved_at = CURRENT_TIMESTAMP(6), resolved_by_user_id = ?
                    WHERE transfer_id = ?
                    """, adminUserId, transferId);
        }
        return getTransfer(transferId);
    }

    @Transactional
    public TransferResponse complete(long transferId, long adminUserId) {
        requireActiveAdmin(adminUserId);
        List<WaitingTransfer> rows = jdbc.query("""
                SELECT provider_user_id, from_business_id, to_business_id, status, effective_at
                FROM provider_transfer WHERE transfer_id = ? FOR UPDATE
                """, (rs, row) -> new WaitingTransfer(rs.getLong("provider_user_id"), rs.getLong("from_business_id"),
                rs.getLong("to_business_id"), rs.getString("status"), rs.getTimestamp("effective_at") == null
                ? null : rs.getTimestamp("effective_at").toLocalDateTime()), transferId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud de transferencia no encontrada");
        WaitingTransfer transfer = rows.getFirst();
        if (!"WAITING".equals(transfer.status())) throw new ResponseStatusException(HttpStatus.CONFLICT, "La transferencia no está en espera");
        if (transfer.effectiveAt() == null || jdbc.queryForObject(
                "SELECT effective_at > CURRENT_TIMESTAMP(6) FROM provider_transfer WHERE transfer_id = ?", Boolean.class, transferId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El periodo de espera de 3 días aún no ha terminado");
        }
        Integer activeDestination = jdbc.queryForObject("SELECT COUNT(*) FROM business WHERE business_id = ? AND status = 'ACTIVE'",
                Integer.class, transfer.toBusinessId());
        if (activeDestination == null || activeDestination == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El negocio de destino ya no está activo");
        }
        List<Long> currentAssignments = jdbc.query("""
                SELECT assignment_id FROM provider_assignment
                WHERE provider_user_id = ? AND business_id = ? AND ended_at IS NULL FOR UPDATE
                """, (rs, row) -> rs.getLong("assignment_id"), transfer.providerUserId(), transfer.fromBusinessId());
        if (currentAssignments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La asignación actual del proveedor cambió");
        }
        Timestamp effectiveAt = Timestamp.valueOf(transfer.effectiveAt());
        int closed = jdbc.update("UPDATE provider_assignment SET ended_at = ? WHERE assignment_id = ? AND ended_at IS NULL",
                effectiveAt, currentAssignments.getFirst());
        if (closed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "No se pudo cerrar la asignación actual");
        jdbc.update("INSERT INTO provider_assignment (provider_user_id, business_id, started_at) VALUES (?, ?, ?)",
                transfer.providerUserId(), transfer.toBusinessId(), effectiveAt);
        jdbc.update("""
                UPDATE provider_transfer SET status = 'COMPLETED', completed_at = CURRENT_TIMESTAMP(6),
                    completed_by_user_id = ? WHERE transfer_id = ?
                """, adminUserId, transferId);
        return getTransfer(transferId);
    }

    private void requireActiveAdmin(long userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM app_user u JOIN user_role ur ON ur.user_id = u.user_id
                JOIN role r ON r.role_id = ur.role_id
                WHERE u.user_id = ? AND u.account_status = 'ACTIVE' AND r.role_code = 'ADMIN'
                """, Integer.class, userId);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere un administrador activo");
    }

    private TransferResponse getTransfer(long id) {
        return jdbc.query(selectTransferSql() + " WHERE t.transfer_id = ?", (rs, row) -> mapTransfer(rs), id)
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud de transferencia no encontrada"));
    }

    private String selectTransferSql() {
        return """
                SELECT t.transfer_id, t.provider_user_id, u.display_name AS provider_name, u.email AS provider_email,
                       t.from_business_id, fb.legal_name AS from_business_name,
                       t.to_business_id, tb.legal_name AS to_business_name,
                       t.requested_at, t.status, t.from_business_decision, t.from_business_decided_at,
                       t.from_business_decided_by_user_id, t.to_business_decision, t.to_business_decided_at,
                       t.to_business_decided_by_user_id, t.approved_at, t.effective_at, t.completed_at,
                       t.completed_by_user_id
                FROM provider_transfer t
                JOIN app_user u ON u.user_id = t.provider_user_id
                JOIN business fb ON fb.business_id = t.from_business_id
                JOIN business tb ON tb.business_id = t.to_business_id
                """;
    }

    private TransferResponse mapTransfer(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TransferResponse(rs.getLong("transfer_id"), rs.getLong("provider_user_id"),
                rs.getString("provider_name"), rs.getString("provider_email"), rs.getLong("from_business_id"),
                rs.getString("from_business_name"), rs.getLong("to_business_id"), rs.getString("to_business_name"),
                rs.getTimestamp("requested_at").toLocalDateTime(), rs.getString("status"),
                rs.getString("from_business_decision"), localDateTime(rs, "from_business_decided_at"),
                nullableLong(rs, "from_business_decided_by_user_id"), rs.getString("to_business_decision"),
                localDateTime(rs, "to_business_decided_at"), nullableLong(rs, "to_business_decided_by_user_id"),
                localDateTime(rs, "approved_at"), localDateTime(rs, "effective_at"), localDateTime(rs, "completed_at"),
                nullableLong(rs, "completed_by_user_id"));
    }

    private LocalDateTime localDateTime(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        java.sql.Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private record ProviderAssignment(long businessId, String status) { }
    private record Business(long id, String name) { }
    private record TransferRow(String status, String fromDecision, String toDecision) { }
    private record WaitingTransfer(long providerUserId, long fromBusinessId, long toBusinessId, String status,
                                   LocalDateTime effectiveAt) { }
    public record TransferResponse(long transferId, long providerUserId, String providerName, String providerEmail,
                                   long fromBusinessId, String fromBusinessName, long toBusinessId, String toBusinessName,
                                   LocalDateTime requestedAt, String status, String fromBusinessDecision,
                                   LocalDateTime fromBusinessDecidedAt, Long fromBusinessDecidedByUserId,
                                   String toBusinessDecision, LocalDateTime toBusinessDecidedAt,
                                   Long toBusinessDecidedByUserId, LocalDateTime approvedAt, LocalDateTime effectiveAt,
                                   LocalDateTime completedAt, Long completedByUserId) { }
}
