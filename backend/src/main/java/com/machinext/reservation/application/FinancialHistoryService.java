package com.machinext.reservation.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FinancialHistoryService {
    private final JdbcTemplate jdbc;

    public FinancialHistoryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<PaymentOperation> listPaymentsForCustomer(long customerId) {
        return listPayments("r.customer_user_id = ?", customerId);
    }

    public List<PaymentOperation> listPaymentsForProvider(long providerId) {
        long businessId = currentBusinessId(providerId);
        return listPayments("r.business_id = ?", businessId);
    }

    public List<PaymentOperation> listAllPayments() {
        return listPayments("1 = 1", null);
    }

    public List<InvoiceSummary> listInvoicesForProvider(long providerId) {
        long businessId = currentBusinessId(providerId);
        return listInvoices("i.business_id = ?", businessId);
    }

    public List<InvoiceSummary> listAllInvoices() {
        return listInvoices("1 = 1", null);
    }

    private List<PaymentOperation> listPayments(String scope, Long ownerId) {
        String sql = """
                SELECT po.payment_operation_id, po.reservation_id, po.operation_type, po.amount, po.currency,
                       po.status, po.created_at, u.display_name AS customer_name, b.legal_name AS business_name
                FROM payment_operation po
                JOIN reservation r ON r.reservation_id = po.reservation_id
                JOIN app_user u ON u.user_id = r.customer_user_id
                JOIN business b ON b.business_id = r.business_id
                WHERE
                """ + scope + " ORDER BY po.created_at DESC, po.payment_operation_id DESC";
        return ownerId == null ? jdbc.query(sql, (rs, row) -> new PaymentOperation(rs.getLong("payment_operation_id"),
                rs.getLong("reservation_id"), rs.getString("operation_type"), rs.getBigDecimal("amount"),
                rs.getString("currency"), rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("customer_name"), rs.getString("business_name"), "SIMULATED"))
                : jdbc.query(sql, (rs, row) -> new PaymentOperation(rs.getLong("payment_operation_id"),
                rs.getLong("reservation_id"), rs.getString("operation_type"), rs.getBigDecimal("amount"),
                rs.getString("currency"), rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getString("customer_name"), rs.getString("business_name"), "SIMULATED"), ownerId);
    }

    private List<InvoiceSummary> listInvoices(String scope, Long ownerId) {
        String sql = """
                SELECT i.invoice_id, i.reservation_id, i.invoice_number, i.status, i.issued_at,
                       i.total_amount, i.currency, u.display_name AS customer_name, b.legal_name AS business_name
                FROM invoice i
                JOIN reservation r ON r.reservation_id = i.reservation_id
                JOIN app_user u ON u.user_id = r.customer_user_id
                JOIN business b ON b.business_id = i.business_id
                WHERE
                """ + scope + " ORDER BY i.issued_at DESC, i.invoice_id DESC";
        return ownerId == null ? jdbc.query(sql, this::invoiceRow)
                : jdbc.query(sql, this::invoiceRow, ownerId);
    }

    private InvoiceSummary invoiceRow(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new InvoiceSummary(rs.getLong("invoice_id"), rs.getLong("reservation_id"),
                rs.getString("invoice_number"), rs.getString("status"), rs.getTimestamp("issued_at").toLocalDateTime(),
                rs.getBigDecimal("total_amount"), rs.getString("currency"), rs.getString("customer_name"),
                rs.getString("business_name"));
    }

    private long currentBusinessId(long providerId) {
        List<Long> businessIds = jdbc.query("""
                SELECT pa.business_id FROM provider_assignment pa
                JOIN app_user u ON u.user_id = pa.provider_user_id AND u.account_status = 'ACTIVE'
                JOIN user_role ur ON ur.user_id = u.user_id
                JOIN role ro ON ro.role_id = ur.role_id AND ro.role_code = 'PROVEEDOR'
                JOIN business b ON b.business_id = pa.business_id AND b.status = 'ACTIVE'
                WHERE pa.provider_user_id = ? AND pa.ended_at IS NULL
                """, (rs, row) -> rs.getLong(1), providerId);
        if (businessIds.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes un negocio activo asignado");
        return businessIds.getFirst();
    }

    public record PaymentOperation(long operationId, long reservationId, String operationType,
                                   BigDecimal amount, String currency, String status, LocalDateTime createdAt,
                                   String customerName, String businessName, String paymentMode) { }

    public record InvoiceSummary(long invoiceId, long reservationId, String invoiceNumber, String status,
                                 LocalDateTime issuedAt, BigDecimal totalAmount, String currency,
                                 String customerName, String businessName) { }
}
