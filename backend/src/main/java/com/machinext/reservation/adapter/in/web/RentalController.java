package com.machinext.reservation.adapter.in.web;

import com.machinext.reservation.application.ReservationService;
import com.machinext.reservation.adapter.out.storage.DamageEvidenceStorage;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rentals")
@SecurityRequirement(name = "bearerAuth")
public class RentalController {
    private final ReservationService reservationService;
    private final DamageEvidenceStorage evidenceStorage;

    public RentalController(ReservationService reservationService, DamageEvidenceStorage evidenceStorage) {
        this.reservationService = reservationService;
        this.evidenceStorage = evidenceStorage;
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

    @PostMapping(value = "/{reservationId}/damage-evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReservationService.EvidenceResponse uploadDamageEvidence(@AuthenticationPrincipal Jwt jwt,
                                                                     @PathVariable long reservationId,
                                                                     @RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un archivo de evidencia");
        }
        String mediaType = file.getContentType();
        if (mediaType == null || !List.of("image/jpeg", "image/png", "video/mp4", "application/pdf").contains(mediaType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa una imagen JPG/PNG, un video MP4 o un PDF");
        }
        String storageKey;
        try {
            storageKey = evidenceStorage.store(file);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la evidencia");
        }
        try {
            return reservationService.attachDamageEvidence(Long.parseLong(jwt.getSubject()), reservationId,
                    storageKey, mediaType);
        } catch (RuntimeException exception) {
            try {
                evidenceStorage.delete(storageKey);
            } catch (IOException ignored) {
                // A later local cleanup can remove this unreferenced file.
            }
            throw exception;
        }
    }

    public record ReturnRequest(@NotBlank @Pattern(regexp = "GOOD|PARTIAL_DAMAGE|TOTAL_DAMAGE") String condition) { }
}
