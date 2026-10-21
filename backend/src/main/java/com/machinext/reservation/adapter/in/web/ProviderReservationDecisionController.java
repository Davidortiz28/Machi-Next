package com.machinext.reservation.adapter.in.web;

import com.machinext.reservation.application.ReservationService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/provider/reservations")
@SecurityRequirement(name = "bearerAuth")
public class ProviderReservationDecisionController {
    private final ReservationService reservations;

    public ProviderReservationDecisionController(ReservationService reservations) { this.reservations = reservations; }

    @PostMapping("/{reservationId}/decision")
    public ReservationService.ReservationDecisionResponse decide(@AuthenticationPrincipal Jwt jwt,
            @PathVariable long reservationId, @Valid @RequestBody DecisionRequest request) {
        return reservations.decide(Long.parseLong(jwt.getSubject()), reservationId, request.decision());
    }

    public record DecisionRequest(@NotBlank @Pattern(regexp = "ACCEPT|REJECT") String decision) { }
}
