package com.machinext.reservation.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

public final class CancellationPolicy {
    private CancellationPolicy() { }

    public static int refundPercent(LocalDateTime cancelledAt, LocalDateTime scheduledStartAt) {
        Objects.requireNonNull(cancelledAt, "La fecha de cancelación es obligatoria");
        Objects.requireNonNull(scheduledStartAt, "La fecha de inicio es obligatoria");
        return Duration.between(cancelledAt, scheduledStartAt).compareTo(Duration.ofHours(24)) >= 0 ? 100 : 90;
    }
}
