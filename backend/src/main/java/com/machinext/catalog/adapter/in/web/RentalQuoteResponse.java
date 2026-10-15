package com.machinext.catalog.adapter.in.web;

import java.math.BigDecimal;

public record RentalQuoteResponse(
        long calendarDays,
        BigDecimal dailyAmount,
        BigDecimal rentalAmount,
        BigDecimal depositAmount,
        String currency) {
}
