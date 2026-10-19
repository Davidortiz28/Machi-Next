package com.machinext.identity.adapter.in.web;

import com.machinext.identity.application.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request.email(), request.password(), request.displayName());
    }

    @PostMapping("/login")
    public AuthService.AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/bootstrap-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResponse bootstrapAdmin(@Valid @RequestBody BootstrapAdminRequest request) {
        return authService.bootstrapAdmin(request.email(), request.password(), request.displayName(), request.bootstrapToken());
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 160) String displayName) { }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank String password) { }

    public record BootstrapAdminRequest(@NotBlank @Email @Size(max = 254) String email,
                                        @NotBlank @Size(min = 12, max = 72) String password,
                                        @NotBlank @Size(max = 160) String displayName,
                                        @NotBlank @Size(min = 32, max = 256) String bootstrapToken) { }
}
