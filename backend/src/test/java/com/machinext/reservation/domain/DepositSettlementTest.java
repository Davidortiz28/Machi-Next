package com.machinext.reservation.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DepositSettlementTest {
    @Test
    void approvedPartialDamageKeepsOnlyTheApprovedAmount() {
        DepositSettlement result = DepositSettlement.approve(new BigDecimal("100000"),
                new BigDecimal("40000"), new BigDecimal("30000"));

        assertEquals(new BigDecimal("30000"), result.retainedAmount());
        assertEquals(new BigDecimal("70000"), result.refundAmount());
    }

    @Test
    void rejectedDamageReturnsTheFullDeposit() {
        DepositSettlement result = DepositSettlement.reject(new BigDecimal("100000"), new BigDecimal("25000"));

        assertEquals(BigDecimal.ZERO, result.retainedAmount());
        assertEquals(new BigDecimal("100000"), result.refundAmount());
    }

    @Test
    void retentionCannotExceedDepositOrAssessment() {
        assertThrows(IllegalArgumentException.class, () -> DepositSettlement.approve(new BigDecimal("100000"),
                new BigDecimal("120000"), new BigDecimal("110000")));
        assertThrows(IllegalArgumentException.class, () -> DepositSettlement.approve(new BigDecimal("100000"),
                new BigDecimal("90000"), new BigDecimal("110000")));
    }
}
