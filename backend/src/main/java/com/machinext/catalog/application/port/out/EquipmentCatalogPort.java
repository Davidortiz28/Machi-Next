package com.machinext.catalog.application.port.out;

import com.machinext.catalog.domain.CatalogItem;
import com.machinext.catalog.domain.RentalPeriod;

import java.util.List;

public interface EquipmentCatalogPort {

    List<CatalogItem> findCurrentCatalog(String search, String category);

    List<CatalogItem> findAvailable(RentalPeriod period, String search, String category);
}
