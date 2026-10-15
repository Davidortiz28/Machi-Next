package com.machinext.identity.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final long tokenTtlSeconds;

    public AuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       @Value("${app.security.jwt-ttl-minutes}") long tokenTtlMinutes) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.tokenTtlSeconds = tokenTtlMinutes * 60;
    }

    @Transactional
    public AuthResponse register(String email, String password, String displayName) {
        String normalizedEmail = normalizeEmail(email);
        try {
            jdbc.update("INSERT INTO app_user (email, password_hash, display_name, account_status) VALUES (?, ?, ?, 'ACTIVE')",
                    normalizedEmail, passwordEncoder.encode(password), displayName.trim());
            Long userId = jdbc.queryForObject("SELECT user_id FROM app_user WHERE email = ?", Long.class, normalizedEmail);
            jdbc.update("INSERT INTO user_role (user_id, role_id) SELECT ?, role_id FROM role WHERE role_code = 'CLIENTE'", userId);
            return issueToken(userId, normalizedEmail, displayName.trim(), List.of("CLIENTE"));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una cuenta con ese correo");
        }
    }

    public AuthResponse login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        List<Account> accounts = jdbc.query("""
                SELECT u.user_id, u.email, u.password_hash, u.display_name, u.account_status,
                       GROUP_CONCAT(r.role_code ORDER BY r.role_code) AS roles
                FROM app_user u
                JOIN user_role ur ON ur.user_id = u.user_id
                JOIN role r ON r.role_id = ur.role_id
                WHERE u.email = ?
                GROUP BY u.user_id, u.email, u.password_hash, u.display_name, u.account_status
                """, (rs, rowNum) -> new Account(rs.getLong("user_id"), rs.getString("email"),
                rs.getString("password_hash"), rs.getString("display_name"), rs.getString("account_status"),
                rs.getString("roles")), normalizedEmail);
        if (accounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }
        Account account = accounts.getFirst();
        if (!"ACTIVE".equals(account.status()) || !passwordEncoder.matches(password, account.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos");
        }
        return issueToken(account.id(), account.email(), account.displayName(), List.of(account.roles().split(",")));
    }

    private AuthResponse issueToken(Long userId, String email, String displayName, List<String> roles) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(tokenTtlSeconds);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("machi-next-api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(userId.toString())
                .claim("email", email)
                .claim("name", displayName)
                .claim("roles", roles)
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse("Bearer", token, tokenTtlSeconds, userId, email, displayName, roles);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record Account(Long id, String email, String passwordHash, String displayName, String status, String roles) { }

    public record AuthResponse(String tokenType, String accessToken, long expiresIn, Long userId,
                               String email, String displayName, List<String> roles) { }
}
