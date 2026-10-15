package com.machinext.catalog.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record RentalPeriod(LocalDate startDate, LocalDate returnDate) {

    public RentalPeriod {
        Objects.requireNonNull(startDate, "La fecha de inicio es obligatoria");
        Objects.requireNonNull(returnDate, "La fecha de devolución es obligatoria");
        if (returnDate.isBefore(startDate)) {
            throw new InvalidRentalPeriodException("La fecha de devolución no puede ser anterior al inicio.");
        }
    }

    public long calendarDaysInclusive() {
        return ChronoUnit.DAYS.between(startDate, returnDate) + 1;
    }
}
