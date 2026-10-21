package com.machinext.identity.adapter.in.web;

import com.machinext.reservation.application.FinancialHistoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/financial-history")
@SecurityRequirement(name = "bearerAuth")
public class AdminFinancialHistoryController {
    private final FinancialHistoryService history;

    public AdminFinancialHistoryController(FinancialHistoryService history) { this.history = history; }

    @GetMapping("/payments")
    public List<FinancialHistoryService.PaymentOperation> payments() { return history.listAllPayments(); }

    @GetMapping("/invoices")
    public List<FinancialHistoryService.InvoiceSummary> invoices() { return history.listAllInvoices(); }
}
