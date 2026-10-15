package com.machinext.catalog.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record RentalQuote(
        long calendarDays,
        BigDecimal dailyAmount,
        BigDecimal rentalAmount,
        BigDecimal depositAmount,
        String currency) {

    public static RentalQuote forPeriod(RentalPeriod period, CatalogItem equipment) {
        Objects.requireNonNull(period, "El período de renta es obligatorio");
        Objects.requireNonNull(equipment, "El equipo es obligatorio");

        long days = period.calendarDaysInclusive();
        BigDecimal rentalAmount = equipment.dailyAmount().multiply(BigDecimal.valueOf(days));

        return new RentalQuote(
                days,
                equipment.dailyAmount(),
                rentalAmount,
                equipment.depositAmount(),
                equipment.currency());
    }
}
