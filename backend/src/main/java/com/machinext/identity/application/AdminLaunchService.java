package com.machinext.identity.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminLaunchService {
    private static final long CODE_TTL_SECONDS = 60;
    private final AuthService authService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, LaunchGrant> grants = new ConcurrentHashMap<>();

    public AdminLaunchService(AuthService authService) {
        this.authService = authService;
    }

    public LaunchCode create(String email, String password) {
        AuthService.AuthResponse session = authService.login(email, password);
        if (!session.roles().contains("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "La cuenta no tiene acceso administrativo");
        }
        Instant now = Instant.now();
        grants.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        grants.put(code, new LaunchGrant(session, now.plusSeconds(CODE_TTL_SECONDS)));
        return new LaunchCode(code, CODE_TTL_SECONDS);
    }

    public AuthService.AuthResponse consume(String code) {
        LaunchGrant grant = code == null ? null : grants.remove(code);
        if (grant == null || !grant.expiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "El código de acceso venció o ya fue utilizado");
        }
        return grant.session();
    }

    private record LaunchGrant(AuthService.AuthResponse session, Instant expiresAt) { }
    public record LaunchCode(String code, long expiresIn) { }
}
