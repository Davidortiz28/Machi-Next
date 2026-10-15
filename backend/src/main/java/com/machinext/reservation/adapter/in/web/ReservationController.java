package com.machinext.reservation.adapter.in.web;

import com.machinext.reservation.application.ReservationService;
import com.machinext.catalog.domain.InvalidRentalPeriodException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ReservationService.ReservationResponse create(@AuthenticationPrincipal Jwt jwt,
                                                          @Valid @RequestBody CreateReservationRequest request) {
        return reservationService.create(Long.parseLong(jwt.getSubject()), request.equipmentId(),
                request.startDate(), request.returnDate());
    }

    @GetMapping
    public List<ReservationService.ReservationResponse> listMine(@AuthenticationPrincipal Jwt jwt) {
        return reservationService.listForCustomer(Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/{reservationId}/cancel")
    public ReservationService.CancellationResponse cancel(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable long reservationId,
                                                           @Valid @RequestBody(required = false) CancelReservationRequest request) {
        return reservationService.cancel(Long.parseLong(jwt.getSubject()), reservationId,
                request == null ? null : request.reason());
    }

    @ExceptionHandler(InvalidRentalPeriodException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRentalPeriod(InvalidRentalPeriodException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        return ResponseEntity.badRequest().body(problem);
    }

    public record CreateReservationRequest(@NotNull @Positive Long equipmentId,
                                           @NotNull LocalDate startDate,
                                           @NotNull LocalDate returnDate) { }

    public record CancelReservationRequest(@jakarta.validation.constraints.Size(max = 500) String reason) { }
}
