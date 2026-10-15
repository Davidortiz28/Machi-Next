package com.machinext.catalog.adapter.in.web;

import com.machinext.catalog.application.ListEquipmentCatalog;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog/equipment")
public class EquipmentCatalogController {

    private final ListEquipmentCatalog listEquipmentCatalog;

    public EquipmentCatalogController(ListEquipmentCatalog listEquipmentCatalog) {
        this.listEquipmentCatalog = listEquipmentCatalog;
    }

    @GetMapping
    public List<EquipmentCatalogResponse> listEquipment() {
        return listEquipmentCatalog.execute().stream()
                .map(item -> new EquipmentCatalogResponse(
                        item.equipmentId(),
                        item.businessName(),
                        item.categoryName(),
                        item.manufacturer(),
                        item.modelName(),
                        item.description(),
                        item.dailyAmount(),
                        item.depositAmount(),
                        item.currency()))
                .toList();
    }
}
