package com.machinext.identity.adapter.in.web;

import com.machinext.identity.application.ProviderTransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business/provider-transfers")
public class BusinessTransferController {
    private final ProviderTransferService transferService;

    public BusinessTransferController(ProviderTransferService transferService) { this.transferService = transferService; }

    @GetMapping
    public List<ProviderTransferService.TransferResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return transferService.listForBusinessManager(Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/{transferId}/source-decision")
    public ProviderTransferService.TransferResponse decideSource(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long transferId, @Valid @RequestBody DecisionRequest request) {
        return transferService.decide(transferId, Long.parseLong(jwt.getSubject()), "SOURCE", request.decision());
    }

    @PostMapping("/{transferId}/destination-decision")
    public ProviderTransferService.TransferResponse decideDestination(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long transferId, @Valid @RequestBody DecisionRequest request) {
        return transferService.decide(transferId, Long.parseLong(jwt.getSubject()), "DESTINATION", request.decision());
    }

    public record DecisionRequest(@NotBlank @Pattern(regexp = "APPROVED|REJECTED") String decision) { }
}
