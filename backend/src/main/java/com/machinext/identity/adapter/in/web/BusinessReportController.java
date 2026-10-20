package com.machinext.identity.adapter.in.web;

import com.machinext.identity.application.BusinessReportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/business/reports")
public class BusinessReportController {
    private final BusinessReportService reports;

    public BusinessReportController(BusinessReportService reports) { this.reports = reports; }

    @GetMapping
    public List<BusinessReportService.BusinessReport> list(@AuthenticationPrincipal Jwt jwt) {
        return reports.listForManager(Long.parseLong(jwt.getSubject()));
    }
}
