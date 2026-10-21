package com.machinext.reservation.adapter.in.web;

import com.machinext.reservation.application.FinancialHistoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/provider/financial-history")
@SecurityRequirement(name = "bearerAuth")
public class ProviderFinancialHistoryController {
    private final FinancialHistoryService history;

    public ProviderFinancialHistoryController(FinancialHistoryService history) { this.history = history; }

    @GetMapping("/payments")
    public List<FinancialHistoryService.PaymentOperation> payments(@AuthenticationPrincipal Jwt jwt) {
        return history.listPaymentsForProvider(Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/invoices")
    public List<FinancialHistoryService.InvoiceSummary> invoices(@AuthenticationPrincipal Jwt jwt) {
        return history.listInvoicesForProvider(Long.parseLong(jwt.getSubject()));
    }
}
