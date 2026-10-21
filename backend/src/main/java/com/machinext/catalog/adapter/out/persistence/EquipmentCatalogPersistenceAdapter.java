package com.machinext.catalog.adapter.out.persistence;

import com.machinext.catalog.application.port.out.EquipmentCatalogPort;
import com.machinext.catalog.domain.CatalogItem;
import com.machinext.catalog.domain.RentalPeriod;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EquipmentCatalogPersistenceAdapter implements EquipmentCatalogPort {

    private final EquipmentCatalogJpaRepository repository;

    public EquipmentCatalogPersistenceAdapter(EquipmentCatalogJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CatalogItem> findCurrentCatalog(String search, String category) {
        return repository.findCurrentCatalog(search, category).stream().map(this::toDomain).toList();
    }

    @Override
    public List<CatalogItem> findAvailable(RentalPeriod period, String search, String category) {
        return repository.findAvailable(period.startDate(), period.returnDate(), search, category).stream()
                .map(this::toDomain)
                .toList();
    }

    private CatalogItem toDomain(EquipmentCatalogProjection row) {
        return new CatalogItem(
                row.getEquipmentId(),
                row.getBusinessName(),
                row.getCategoryName(),
                row.getManufacturer(),
                row.getModelName(),
                row.getDescription(),
                row.getDailyAmount(),
                row.getDepositAmount(),
                row.getCurrency());
    }
}
