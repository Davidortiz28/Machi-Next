package com.machinext.catalog.adapter.configuration;

import com.machinext.catalog.application.ListEquipmentCatalog;
import com.machinext.catalog.application.FindAvailableEquipment;
import com.machinext.catalog.application.port.out.EquipmentCatalogPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogConfiguration {

    @Bean
    ListEquipmentCatalog listEquipmentCatalog(EquipmentCatalogPort equipmentCatalogPort) {
        return new ListEquipmentCatalog(equipmentCatalogPort);
    }

    @Bean
    FindAvailableEquipment findAvailableEquipment(EquipmentCatalogPort equipmentCatalogPort) {
        return new FindAvailableEquipment(equipmentCatalogPort);
    }
}
