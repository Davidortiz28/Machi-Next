package com.machinext.reservation.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CancellationPolicyTest {
    @Test
    void returnsAllMoneyWhenCancelledAtLeastTwentyFourHoursBeforeStart() {
        LocalDateTime cancellation = LocalDateTime.of(2026, 10, 15, 10, 0);

        assertEquals(100, CancellationPolicy.refundPercent(cancellation, cancellation.plusHours(24)));
        assertEquals(100, CancellationPolicy.refundPercent(cancellation, cancellation.plusHours(25)));
    }

    @Test
    void returnsNinetyPercentWhenCancelledWithLessThanTwentyFourHoursNotice() {
        LocalDateTime cancellation = LocalDateTime.of(2026, 10, 15, 10, 0);

        assertEquals(90, CancellationPolicy.refundPercent(cancellation, cancellation.plusHours(23).plusMinutes(59)));
    }
}
