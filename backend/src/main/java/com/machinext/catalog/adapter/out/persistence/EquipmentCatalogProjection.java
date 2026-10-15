package com.machinext.catalog.adapter.out.persistence;

import java.math.BigDecimal;

public interface EquipmentCatalogProjection {

    Long getEquipmentId();

    String getBusinessName();

    String getCategoryName();

    String getManufacturer();

    String getModelName();

    String getDescription();

    BigDecimal getDailyAmount();

    BigDecimal getDepositAmount();

    String getCurrency();
}
