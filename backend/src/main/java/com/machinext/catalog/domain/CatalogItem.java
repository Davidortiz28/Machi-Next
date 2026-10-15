package com.machinext.catalog.domain;

import java.math.BigDecimal;

public record CatalogItem(
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
