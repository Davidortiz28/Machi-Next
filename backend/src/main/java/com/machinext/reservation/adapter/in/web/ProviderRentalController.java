package com.machinext.reservation.adapter.in.web;

import com.machinext.reservation.application.ReservationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/provider/rentals")
public class ProviderRentalController {
    private final ReservationService reservationService;

    public ProviderRentalController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping
    public List<ReservationService.ProviderRentalResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return reservationService.listForProvider(Long.parseLong(jwt.getSubject()));
    }
}
