package com.machinext.reservation.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record DepositSettlement(BigDecimal assessedAmount, BigDecimal retainedAmount, BigDecimal refundAmount) {
    public DepositSettlement {
        Objects.requireNonNull(assessedAmount);
        Objects.requireNonNull(retainedAmount);
        Objects.requireNonNull(refundAmount);
        if (assessedAmount.signum() < 0 || retainedAmount.signum() < 0 || refundAmount.signum() < 0
                || retainedAmount.compareTo(assessedAmount) > 0) {
            throw new IllegalArgumentException("Los valores de la liquidación del depósito no son válidos");
        }
    }

    public static DepositSettlement approve(BigDecimal deposit, BigDecimal assessed, BigDecimal retained) {
        validateDepositAmounts(deposit, assessed, retained);
        return new DepositSettlement(assessed, retained, deposit.subtract(retained));
    }

    public static DepositSettlement reject(BigDecimal deposit, BigDecimal assessed) {
        validateDepositAmounts(deposit, assessed, BigDecimal.ZERO);
        return new DepositSettlement(assessed, BigDecimal.ZERO, deposit);
    }

    private static void validateDepositAmounts(BigDecimal deposit, BigDecimal assessed, BigDecimal retained) {
        Objects.requireNonNull(deposit);
        Objects.requireNonNull(assessed);
        Objects.requireNonNull(retained);
        if (deposit.signum() < 0 || assessed.signum() < 0 || retained.signum() < 0
                || retained.compareTo(assessed) > 0 || retained.compareTo(deposit) > 0) {
            throw new IllegalArgumentException("La retención no puede superar el daño ni el depósito");
        }
    }
}
