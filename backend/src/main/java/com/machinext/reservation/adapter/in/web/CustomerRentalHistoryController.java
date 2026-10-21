package com.machinext.reservation.adapter.in.web;

import com.machinext.reservation.application.ReservationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations/history")
@SecurityRequirement(name = "bearerAuth")
public class CustomerRentalHistoryController {
    private final ReservationService reservations;

    public CustomerRentalHistoryController(ReservationService reservations) { this.reservations = reservations; }

    @GetMapping
    public List<ReservationService.CustomerRentalHistory> list(@AuthenticationPrincipal Jwt jwt) {
        return reservations.rentalHistoryForCustomer(Long.parseLong(jwt.getSubject()));
    }
}
