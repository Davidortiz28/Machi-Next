package com.machinext.catalog.adapter.out.persistence;

import com.machinext.catalog.application.port.out.EquipmentCatalogPort;
import com.machinext.catalog.domain.CatalogItem;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EquipmentCatalogPersistenceAdapter implements EquipmentCatalogPort {

    private final EquipmentCatalogJpaRepository repository;

    public EquipmentCatalogPersistenceAdapter(EquipmentCatalogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CatalogItem> findCurrentCatalog() {
        return repository.findCurrentCatalog().stream()
                .map(row -> new CatalogItem(
                        row.getEquipmentId(),
                        row.getBusinessName(),
                        row.getCategoryName(),
                        row.getManufacturer(),
                        row.getModelName(),
                        row.getDescription(),
                        row.getDailyAmount(),
                        row.getDepositAmount(),
                        row.getCurrency()))
                .toList();
    }
}
