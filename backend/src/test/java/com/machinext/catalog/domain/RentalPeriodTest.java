package com.machinext.catalog.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RentalPeriodTest {

    @Test
    void countsBothStartAndReturnDates() {
        RentalPeriod period = new RentalPeriod(
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 17));

        assertEquals(3, period.calendarDaysInclusive());
    }

    @Test
    void aSameDayRentalCountsAsOneCalendarDay() {
        RentalPeriod period = new RentalPeriod(
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 15));

        assertEquals(1, period.calendarDaysInclusive());
    }

    @Test
    void rejectsReturnDateBeforeStartDate() {
        assertThrows(InvalidRentalPeriodException.class, () -> new RentalPeriod(
                LocalDate.of(2026, 10, 16),
                LocalDate.of(2026, 10, 15)));
    }
}
