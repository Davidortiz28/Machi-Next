package com.machinext.catalog.application;

import com.machinext.catalog.application.port.out.EquipmentCatalogPort;
import com.machinext.catalog.domain.CatalogItem;
import com.machinext.catalog.domain.AvailableEquipment;
import com.machinext.catalog.domain.RentalQuote;
import com.machinext.catalog.domain.RentalPeriod;

import java.util.List;

public class FindAvailableEquipment {

    private final EquipmentCatalogPort equipmentCatalog;

    public FindAvailableEquipment(EquipmentCatalogPort equipmentCatalog) {
        this.equipmentCatalog = equipmentCatalog;
    }

    public List<AvailableEquipment> execute(RentalPeriod period) {
        return equipmentCatalog.findAvailable(period).stream()
                .map(equipment -> new AvailableEquipment(equipment, RentalQuote.forPeriod(period, equipment)))
                .toList();
    }
}
