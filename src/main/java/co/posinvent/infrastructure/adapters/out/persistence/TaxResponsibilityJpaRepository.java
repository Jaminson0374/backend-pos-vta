package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TaxResponsibilityJpaRepository extends JpaRepository<TaxResponsibilityEntity, UUID> {

    List<TaxResponsibilityEntity> findByActiveTrueOrderBySortOrderAsc();
}
