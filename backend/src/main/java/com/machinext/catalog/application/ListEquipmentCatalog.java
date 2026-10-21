package com.machinext.catalog.application;

import com.machinext.catalog.application.port.out.EquipmentCatalogPort;
import com.machinext.catalog.domain.CatalogItem;

import java.util.List;

public class ListEquipmentCatalog {

    private final EquipmentCatalogPort equipmentCatalog;

    public ListEquipmentCatalog(EquipmentCatalogPort equipmentCatalog) {
        this.equipmentCatalog = equipmentCatalog;
    }

    public List<CatalogItem> execute(String search, String category) {
        return equipmentCatalog.findCurrentCatalog(search, category);
    }
}
