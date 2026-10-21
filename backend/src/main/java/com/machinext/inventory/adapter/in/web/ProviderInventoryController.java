package com.machinext.inventory.adapter.in.web;

import com.machinext.inventory.application.ProviderInventoryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/provider/equipment")
@SecurityRequirement(name = "bearerAuth")
public class ProviderInventoryController {
    private final ProviderInventoryService inventory;

    public ProviderInventoryController(ProviderInventoryService inventory) { this.inventory = inventory; }

    @GetMapping
    public List<ProviderInventoryService.InventoryItem> list(Jwt jwt) {
        return inventory.listForProvider(Long.parseLong(jwt.getSubject()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProviderInventoryService.InventoryItem create(Jwt jwt, @Valid @RequestBody CreateEquipmentRequest request) {
        return inventory.create(Long.parseLong(jwt.getSubject()), new ProviderInventoryService.CreateItemCommand(
                request.categoryName(), request.manufacturer(), request.modelName(), request.description(),
                request.serialNumber(), request.dailyAmount(), request.depositAmount(), request.currency(),
                request.validFrom(), request.validTo()));
    }

    @PatchMapping("/{equipmentId}/status")
    public ProviderInventoryService.InventoryItem updateStatus(Jwt jwt, @PathVariable long equipmentId,
                                                                 @Valid @RequestBody UpdateStatusRequest request) {
        return inventory.updateStatus(Long.parseLong(jwt.getSubject()), equipmentId, request.status());
    }

    @PutMapping("/{equipmentId}/rate")
    public ProviderInventoryService.InventoryItem updateRate(Jwt jwt, @PathVariable long equipmentId,
            @Valid @RequestBody UpdateRateRequest request) {
        return inventory.updateRate(Long.parseLong(jwt.getSubject()), equipmentId,
                new ProviderInventoryService.RateCommand(request.dailyAmount(), request.depositAmount(),
                        request.currency(), request.validFrom(), request.validTo()));
    }

    @PutMapping("/{equipmentId}")
    public ProviderInventoryService.InventoryItem updateDetails(Jwt jwt, @PathVariable long equipmentId,
            @Valid @RequestBody UpdateEquipmentRequest request) {
        return inventory.updateDetails(Long.parseLong(jwt.getSubject()), equipmentId,
                new ProviderInventoryService.EquipmentDetailsCommand(request.categoryName(), request.manufacturer(),
                        request.modelName(), request.description(), request.serialNumber()));
    }

    public record CreateEquipmentRequest(
            @NotBlank @Size(max = 100) String categoryName,
            @NotBlank @Size(max = 120) String manufacturer,
            @NotBlank @Size(max = 160) String modelName,
            @Size(max = 1000) String description,
            @NotBlank @Size(max = 100) String serialNumber,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal dailyAmount,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal depositAmount,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo) { }

    public record UpdateStatusRequest(@NotBlank @Pattern(regexp = "AVAILABLE|MAINTENANCE|RETIRED") String status) { }

    public record UpdateRateRequest(
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal dailyAmount,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal depositAmount,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validFrom,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validTo) { }

    public record UpdateEquipmentRequest(
            @NotBlank @Size(max = 100) String categoryName,
            @NotBlank @Size(max = 120) String manufacturer,
            @NotBlank @Size(max = 160) String modelName,
            @Size(max = 1000) String description,
            @NotBlank @Size(max = 100) String serialNumber) { }
}
