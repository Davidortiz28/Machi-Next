package com.machinext.catalog.adapter.out.persistence;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

import java.util.List;

public interface EquipmentCatalogJpaRepository extends Repository<EquipmentUnitJpaEntity, Long> {

    @Query(value = """
            SELECT eu.equipment_id AS equipmentId,
                   b.legal_name AS businessName,
                   c.name AS categoryName,
                   em.manufacturer AS manufacturer,
                   em.model_name AS modelName,
                   em.description AS description,
                   dr.daily_amount AS dailyAmount,
                   dr.deposit_amount AS depositAmount,
                   dr.currency AS currency
            FROM equipment_unit eu
            JOIN business b ON b.business_id = eu.business_id
            JOIN equipment_model em ON em.model_id = eu.model_id
            JOIN category c ON c.category_id = em.category_id
            JOIN daily_rate dr ON dr.equipment_id = eu.equipment_id
            WHERE eu.operational_status = 'AVAILABLE'
              AND b.status = 'ACTIVE'
              AND dr.valid_from <= CURRENT_DATE
              AND (dr.valid_to IS NULL OR dr.valid_to >= CURRENT_DATE)
              AND (COALESCE(:search, '') = '' OR LOWER(CONCAT(em.manufacturer, ' ', em.model_name, ' ', COALESCE(em.description, ''))) LIKE CONCAT('%', LOWER(:search), '%'))
              AND (COALESCE(:category, '') = '' OR LOWER(c.name) = LOWER(:category))
            ORDER BY c.name, em.model_name, eu.equipment_id
            """, nativeQuery = true)
    List<EquipmentCatalogProjection> findCurrentCatalog(@Param("search") String search,
                                                         @Param("category") String category);

    @Query(value = """
            SELECT eu.equipment_id AS equipmentId,
                   b.legal_name AS businessName,
                   c.name AS categoryName,
                   em.manufacturer AS manufacturer,
                   em.model_name AS modelName,
                   em.description AS description,
                   dr.daily_amount AS dailyAmount,
                   dr.deposit_amount AS depositAmount,
                   dr.currency AS currency
            FROM equipment_unit eu
            JOIN business b ON b.business_id = eu.business_id
            JOIN equipment_model em ON em.model_id = eu.model_id
            JOIN category c ON c.category_id = em.category_id
            JOIN daily_rate dr ON dr.equipment_id = eu.equipment_id
            WHERE eu.operational_status = 'AVAILABLE'
              AND b.status = 'ACTIVE'
              AND dr.valid_from <= :startDate
              AND (dr.valid_to IS NULL OR dr.valid_to >= :returnDate)
              AND (COALESCE(:search, '') = '' OR LOWER(CONCAT(em.manufacturer, ' ', em.model_name, ' ', COALESCE(em.description, ''))) LIKE CONCAT('%', LOWER(:search), '%'))
              AND (COALESCE(:category, '') = '' OR LOWER(c.name) = LOWER(:category))
              AND NOT EXISTS (
                  SELECT 1
                  FROM reservation_item ri
                  JOIN reservation r ON r.reservation_id = ri.reservation_id
                  WHERE ri.equipment_id = eu.equipment_id
                    AND r.status IN ('PENDING_PROVIDER', 'CONFIRMED', 'IN_RENTAL')
                    AND ri.start_date <= :returnDate
                    AND ri.return_date >= :startDate
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM reservation_item ri
                  JOIN reservation r ON r.reservation_id = ri.reservation_id
                  JOIN rental rent ON rent.reservation_item_id = ri.reservation_item_id
                  WHERE ri.equipment_id = eu.equipment_id
                    AND r.status = 'IN_RENTAL'
                    AND rent.returned_at IS NULL
              )
            ORDER BY c.name, em.model_name, eu.equipment_id
            """, nativeQuery = true)
    List<EquipmentCatalogProjection> findAvailable(
            @Param("startDate") LocalDate startDate,
            @Param("returnDate") LocalDate returnDate,
            @Param("search") String search,
            @Param("category") String category);
}
