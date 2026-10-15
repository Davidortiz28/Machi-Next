package com.machinext.catalog.adapter.in.web;

import java.time.LocalDate;
import java.util.List;

public record AvailabilityResponse(
        LocalDate startDate,
        LocalDate returnDate,
        long calendarDays,
        List<EquipmentCatalogResponse> equipment) {
}
