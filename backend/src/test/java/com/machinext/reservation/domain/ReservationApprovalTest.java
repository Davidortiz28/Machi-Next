package com.machinext.reservation.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReservationApprovalTest {
    @Test
    void acceptingPendingRequestConfirmsItWithoutRefund() {
        ReservationApproval result = ReservationApproval.decide("PENDING_PROVIDER", "ACCEPT",
                new BigDecimal("80000.00"), new BigDecimal("30000.00"));

        assertEquals("CONFIRMED", result.resultingStatus());
        assertEquals(new BigDecimal("0"), result.refundAmount());
    }

    @Test
    void rejectingPendingRequestRefundsRentAndDepositInFull() {
        ReservationApproval result = ReservationApproval.decide("PENDING_PROVIDER", "REJECT",
                new BigDecimal("80000.00"), new BigDecimal("30000.00"));

        assertEquals("REJECTED", result.resultingStatus());
        assertEquals(new BigDecimal("110000.00"), result.refundAmount());
    }

    @Test
    void aDecisionCannotBeRepeatedAfterTheProviderResponded() {
        assertThrows(IllegalStateException.class, () -> ReservationApproval.decide("CONFIRMED", "REJECT",
                BigDecimal.TEN, BigDecimal.ONE));
    }
}
