package com.machinext.identity.adapter.in.web;

import com.machinext.identity.application.AdminService;
import com.machinext.identity.application.ProviderTransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final ProviderTransferService transferService;

    public AdminController(AdminService adminService, ProviderTransferService transferService) {
        this.adminService = adminService;
        this.transferService = transferService;
    }

    @GetMapping("/businesses")
    public List<AdminService.BusinessSummary> businesses() { return adminService.listBusinesses(); }

    @PostMapping("/providers")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminService.ProviderSummary createProvider(@Valid @RequestBody CreateProviderRequest request) {
        return adminService.createProvider(request.email(), request.password(), request.displayName(), request.businessId());
    }

    @GetMapping("/provider-transfers")
    public List<ProviderTransferService.TransferResponse> providerTransfers() { return transferService.list(); }

    @PostMapping("/provider-transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderTransferService.TransferResponse requestTransfer(@Valid @RequestBody TransferRequest request) {
        return transferService.request(request.providerUserId(), request.toBusinessId());
    }

    @PostMapping("/provider-transfers/{transferId}/source-decision")
    public ProviderTransferService.TransferResponse sourceDecision(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long transferId, @Valid @RequestBody TransferDecision request) {
        return transferService.decide(transferId, Long.parseLong(jwt.getSubject()), "SOURCE", request.decision());
    }

    @PostMapping("/provider-transfers/{transferId}/destination-decision")
    public ProviderTransferService.TransferResponse destinationDecision(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long transferId, @Valid @RequestBody TransferDecision request) {
        return transferService.decide(transferId, Long.parseLong(jwt.getSubject()), "DESTINATION", request.decision());
    }

    @PostMapping("/provider-transfers/{transferId}/complete")
    public ProviderTransferService.TransferResponse completeTransfer(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long transferId) {
        return transferService.complete(transferId, Long.parseLong(jwt.getSubject()));
    }

    public record CreateProviderRequest(@NotBlank @Email @Size(max = 254) String email,
                                        @NotBlank @Size(min = 12, max = 72) String password,
                                        @NotBlank @Size(max = 160) String displayName,
                                        @NotNull @Positive Long businessId) { }

    public record TransferRequest(@NotNull @Positive Long providerUserId,
                                  @NotNull @Positive Long toBusinessId) { }

    public record TransferDecision(@NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision) { }
}
