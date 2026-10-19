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

    public record BusinessSummary(Long businessId, String legalName, String status) { }
    public record ProviderSummary(Long userId, String email, String displayName, BusinessSummary business, String status) { }
}
