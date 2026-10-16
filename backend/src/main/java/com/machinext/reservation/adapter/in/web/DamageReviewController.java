package com.machinext.reservation.adapter.in.web;

import com.machinext.reservation.adapter.out.storage.DamageEvidenceStorage;
import com.machinext.reservation.application.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/damage-reports")
public class DamageReviewController {
    private final ReservationService reservationService;
    private final DamageEvidenceStorage evidenceStorage;

    public DamageReviewController(ReservationService reservationService, DamageEvidenceStorage evidenceStorage) {
        this.reservationService = reservationService;
        this.evidenceStorage = evidenceStorage;
    }

    @GetMapping
    public List<ReservationService.PendingDamageReportResponse> listPending() {
        return reservationService.listPendingDamageReports();
    }

    @GetMapping("/{reportId}/evidence")
    public List<ReservationService.EvidenceResponse> listEvidence(@PathVariable long reportId) {
        return reservationService.listDamageEvidence(reportId);
    }

    @GetMapping("/{reportId}/evidence/{evidenceId}/content")
    public ResponseEntity<Resource> getEvidence(@PathVariable long reportId, @PathVariable long evidenceId) {
        ReservationService.StoredEvidenceResponse evidence = reservationService.findDamageEvidence(reportId, evidenceId);
        Resource content = evidenceStorage.load(evidence.storageKey());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(evidence.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("evidence-" + evidenceId).build().toString())
                .body(content);
    }

    @PostMapping("/{reportId}/review")
    public ReservationService.DepositReviewResponse review(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable long reportId,
                                                            @Valid @RequestBody ReviewRequest request) {
        return reservationService.reviewDamage(Long.parseLong(jwt.getSubject()), reportId, request.decision(),
                request.assessedAmount(), request.retainedAmount(), request.decisionNote());
    }

    public record ReviewRequest(@NotBlank @Pattern(regexp = "APPROVE|REJECT") String decision,
                                @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal assessedAmount,
                                @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal retainedAmount,
                                @Size(max = 1000) String decisionNote) { }
}
