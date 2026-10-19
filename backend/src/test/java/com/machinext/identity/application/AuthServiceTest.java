package com.machinext.identity.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {
    private JdbcTemplate jdbc;
    private PasswordEncoder passwordEncoder;
    private JwtEncoder jwtEncoder;
    private AuthService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtEncoder = mock(JwtEncoder.class);
        service = new AuthService(jdbc, passwordEncoder, jwtEncoder, 60, "a-very-long-local-bootstrap-token-123456");
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("signed-token")
                .header("alg", "HS256").claim("sub", "42").build());
    }

    @Test
    void registrationNormalizesEmailHashesPasswordAndAssignsOnlyCustomerRole() {
        when(passwordEncoder.encode("safe-password")).thenReturn("bcrypt-hash");
        when(jdbc.queryForObject(eq("SELECT user_id FROM app_user WHERE email = ?"), eq(Long.class), eq("user@example.com")))
                .thenReturn(42L);

        AuthService.AuthResponse response = service.register(" User@Example.com ", "safe-password", "  Ana  ");

        assertEquals("signed-token", response.accessToken());
        assertEquals(List.of("CLIENTE"), response.roles());
        assertEquals("user@example.com", response.email());
        verify(jdbc).update(eq("INSERT INTO app_user (email, password_hash, display_name, account_status) VALUES (?, ?, ?, 'ACTIVE')"),
                eq("user@example.com"), eq("bcrypt-hash"), eq("Ana"));
        verify(jdbc).update(eq("INSERT INTO user_role (user_id, role_id) SELECT ?, role_id FROM role WHERE role_code = 'CLIENTE'"), eq(42L));
    }

    @Test
    void bootstrapCreatesTheFirstAdminOnlyWithConfiguredSecret() {
        when(jdbc.queryForObject(eq("SELECT role_id FROM role WHERE role_code = 'ADMIN' FOR UPDATE"), eq(Long.class)))
                .thenReturn(1L);
        when(jdbc.queryForObject(any(String.class), eq(Integer.class), eq(1L))).thenReturn(0);
        when(passwordEncoder.encode("admin-password-123")).thenReturn("admin-hash");
        when(jdbc.queryForObject(eq("SELECT user_id FROM app_user WHERE email = ?"), eq(Long.class), eq("admin@example.com")))
                .thenReturn(50L);

        AuthService.AuthResponse response = service.bootstrapAdmin("Admin@Example.com", "admin-password-123", " Admin ",
                "a-very-long-local-bootstrap-token-123456");

        assertEquals(List.of("ADMIN"), response.roles());
        assertEquals("admin@example.com", response.email());
        verify(jdbc).update(eq("INSERT INTO user_role (user_id, role_id) VALUES (?, ?)"), eq(50L), eq(1L));
    }

}
