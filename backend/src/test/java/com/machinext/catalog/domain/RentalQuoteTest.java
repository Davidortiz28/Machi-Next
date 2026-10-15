package com.machinext.catalog.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RentalQuoteTest {

    @Test
    void calculatesRentalAmountUsingInclusiveCalendarDaysAndKeepsDepositSeparate() {
        CatalogItem equipment = new CatalogItem(
                7L, "Negocio demo", "Eléctricas", "Marca", "Taladro", "Demo",
                new BigDecimal("28000.00"), new BigDecimal("115000.00"), "COP");
        RentalPeriod period = new RentalPeriod(
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 17));

        RentalQuote quote = RentalQuote.forPeriod(period, equipment);

        assertEquals(3, quote.calendarDays());
        assertEquals(new BigDecimal("28000.00"), quote.dailyAmount());
        assertEquals(new BigDecimal("84000.00"), quote.rentalAmount());
        assertEquals(new BigDecimal("115000.00"), quote.depositAmount());
        assertEquals("COP", quote.currency());
    }
}
