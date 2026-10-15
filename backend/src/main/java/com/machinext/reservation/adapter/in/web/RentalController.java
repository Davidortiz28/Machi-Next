package com.machinext.reservation.adapter.in.web;

import com.machinext.reservation.application.ReservationService;
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
@RequestMapping("/api/v1/rentals")
public class RentalController {
    private final ReservationService reservationService;

    public RentalController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/{reservationId}/handover")
    public ReservationService.RentalLifecycleResponse handOver(@AuthenticationPrincipal Jwt jwt,
                                                                 @PathVariable long reservationId) {
        return reservationService.handOver(Long.parseLong(jwt.getSubject()), reservationId);
    }

    @PostMapping("/{reservationId}/return")
    public ReservationService.RentalLifecycleResponse returnRental(@AuthenticationPrincipal Jwt jwt,
                                                                    @PathVariable long reservationId,
                                                                    @Valid @RequestBody ReturnRequest request) {
        return reservationService.returnRental(Long.parseLong(jwt.getSubject()), reservationId, request.condition());
    }

    public record ReturnRequest(@NotBlank @Pattern(regexp = "GOOD|PARTIAL_DAMAGE|TOTAL_DAMAGE") String condition) { }
}
