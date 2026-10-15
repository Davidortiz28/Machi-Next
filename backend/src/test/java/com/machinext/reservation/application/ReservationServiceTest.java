package com.machinext.reservation.application;

import com.machinext.catalog.domain.InvalidRentalPeriodException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ReservationServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ReservationService service = new ReservationService(jdbc);

    @Test
    void rejectsRentalPeriodWithReturnBeforeStart() {
        LocalDate startDate = LocalDate.now().plusDays(2);

        assertThrows(InvalidRentalPeriodException.class,
                () -> service.create(7, 15, startDate, startDate.minusDays(1)));
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsStartDateInThePast() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        assertThrows(ResponseStatusException.class,
                () -> service.create(7, 15, yesterday, yesterday));
        verifyNoInteractions(jdbc);
    }
}
