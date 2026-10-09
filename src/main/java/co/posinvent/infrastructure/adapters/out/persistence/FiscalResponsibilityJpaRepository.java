package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface FiscalResponsibilityJpaRepository extends JpaRepository<FiscalResponsibilityEntity, UUID> {

    List<FiscalResponsibilityEntity> findByActiveTrueOrderBySortOrderAsc();
}
