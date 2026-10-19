package com.machinext.identity.adapter.in.web;

import com.machinext.identity.application.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService adminService;

    public AdminController(AdminService adminService) { this.adminService = adminService; }

    @GetMapping("/businesses")
    public List<AdminService.BusinessSummary> businesses() { return adminService.listBusinesses(); }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminService.ProviderSummary createProvider(@Valid @RequestBody CreateProviderRequest request) {
        return adminService.createProvider(request.email(), request.password(), request.displayName(), request.businessId());
    }

    public record CreateProviderRequest(@NotBlank @Email @Size(max = 254) String email,
                                        @NotBlank @Size(min = 12, max = 72) String password,
                                        @NotBlank @Size(max = 160) String displayName,
                                        @NotNull @Positive Long businessId) { }
}
