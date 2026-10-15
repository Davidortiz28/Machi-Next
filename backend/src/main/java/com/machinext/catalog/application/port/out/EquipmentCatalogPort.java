package com.machinext.catalog.application.port.out;

import com.machinext.catalog.domain.CatalogItem;

import java.util.List;

public interface EquipmentCatalogPort {

    List<CatalogItem> findCurrentCatalog();
}
