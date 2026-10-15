package com.machinext.catalog.domain;

public class InvalidRentalPeriodException extends IllegalArgumentException {

    public InvalidRentalPeriodException(String message) {
        super(message);
    }
}
