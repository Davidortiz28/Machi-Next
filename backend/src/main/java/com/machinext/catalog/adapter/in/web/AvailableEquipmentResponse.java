package com.machinext.catalog.adapter.in.web;

public record AvailableEquipmentResponse(
        EquipmentCatalogResponse equipment,
        RentalQuoteResponse quote) {
}
