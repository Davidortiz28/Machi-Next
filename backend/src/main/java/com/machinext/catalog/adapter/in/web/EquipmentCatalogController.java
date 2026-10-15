package com.machinext.catalog.adapter.in.web;

import com.machinext.catalog.application.ListEquipmentCatalog;
import com.machinext.catalog.application.FindAvailableEquipment;
import com.machinext.catalog.domain.AvailableEquipment;
import com.machinext.catalog.domain.CatalogItem;
import com.machinext.catalog.domain.InvalidRentalPeriodException;
import com.machinext.catalog.domain.RentalPeriod;
import com.machinext.catalog.domain.RentalQuote;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/equipment")
public class EquipmentCatalogController {

    private final ListEquipmentCatalog listEquipmentCatalog;
    private final FindAvailableEquipment findAvailableEquipment;

    public EquipmentCatalogController(
            ListEquipmentCatalog listEquipmentCatalog,
            FindAvailableEquipment findAvailableEquipment) {
        this.listEquipmentCatalog = listEquipmentCatalog;
        this.findAvailableEquipment = findAvailableEquipment;
    }

    @GetMapping
    public List<EquipmentCatalogResponse> listEquipment() {
        return listEquipmentCatalog.execute().stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/availability")
    public AvailabilityResponse findAvailable(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate returnDate) {
        RentalPeriod period = new RentalPeriod(startDate, returnDate);
        List<AvailableEquipmentResponse> equipment = findAvailableEquipment.execute(period).stream()
                .map(this::toAvailableResponse)
                .toList();
        return new AvailabilityResponse(startDate, returnDate, period.calendarDaysInclusive(), equipment);
    }

    @ExceptionHandler(InvalidRentalPeriodException.class)
    public ResponseEntity<ProblemDetail> handleInvalidRentalPeriod(InvalidRentalPeriodException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        return ResponseEntity.badRequest().body(problem);
    }

    private EquipmentCatalogResponse toResponse(CatalogItem item) {
        return new EquipmentCatalogResponse(
                item.equipmentId(),
                item.businessName(),
                item.categoryName(),
                item.manufacturer(),
                item.modelName(),
                item.description(),
                item.dailyAmount(),
                item.depositAmount(),
                item.currency());
    }

    private AvailableEquipmentResponse toAvailableResponse(AvailableEquipment available) {
        RentalQuote quote = available.quote();
        return new AvailableEquipmentResponse(
                toResponse(available.equipment()),
                new RentalQuoteResponse(
                        quote.calendarDays(),
                        quote.dailyAmount(),
                        quote.rentalAmount(),
                        quote.depositAmount(),
                        quote.currency()));
    }
}
