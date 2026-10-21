package com.machinext.reservation.domain;

import java.math.BigDecimal;

public record ReservationApproval(String resultingStatus, BigDecimal refundAmount) {
    public static ReservationApproval decide(String currentStatus, String decision,
                                               BigDecimal rentalAmount, BigDecimal depositAmount) {
        if (!"PENDING_PROVIDER".equals(currentStatus)) {
            throw new IllegalStateException("Solo se puede decidir una solicitud pendiente del proveedor");
        }
        if (rentalAmount == null || depositAmount == null || rentalAmount.signum() < 0 || depositAmount.signum() < 0) {
            throw new IllegalArgumentException("Los importes de la reserva deben ser cero o positivos");
        }
        if ("ACCEPT".equals(decision)) return new ReservationApproval("CONFIRMED", BigDecimal.ZERO);
        if ("REJECT".equals(decision)) return new ReservationApproval("REJECTED", rentalAmount.add(depositAmount));
        throw new IllegalArgumentException("La decisiÃ³n debe ser ACCEPT o REJECT");
    }
}
