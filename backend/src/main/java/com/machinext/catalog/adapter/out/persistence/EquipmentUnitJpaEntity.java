package com.machinext.catalog.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "equipment_unit")
public class EquipmentUnitJpaEntity {

    @Id
    @Column(name = "equipment_id")
    private Long id;

    protected EquipmentUnitJpaEntity() {
    }
}
