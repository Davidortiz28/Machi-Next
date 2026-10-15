package com.machinext.catalog.adapter.in.web;

import java.math.BigDecimal;

public record EquipmentCatalogResponse(
        Long equipmentId,
        String businessName,
        String categoryName,
        String manufacturer,
        String modelName,
        String description,
        BigDecimal dailyAmount,
        BigDecimal depositAmount,
        String currency) {
}
