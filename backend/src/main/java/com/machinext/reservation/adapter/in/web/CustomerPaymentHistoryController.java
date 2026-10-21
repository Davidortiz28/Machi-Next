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
@RequestMapping("/api/v1/reservations/payments")
@SecurityRequirement(name = "bearerAuth")
public class CustomerPaymentHistoryController {
    private final FinancialHistoryService history;

    public CustomerPaymentHistoryController(FinancialHistoryService history) { this.history = history; }

    @GetMapping
    public List<FinancialHistoryService.PaymentOperation> list(@AuthenticationPrincipal Jwt jwt) {
        return history.listPaymentsForCustomer(Long.parseLong(jwt.getSubject()));
    }
}
