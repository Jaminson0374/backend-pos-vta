package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface PucAccountJpaRepository extends JpaRepository<PucAccountEntity, UUID> {
    List<PucAccountEntity> findByActiveTrueOrderByCodeAsc();
    List<PucAccountEntity> findAllByOrderByCodeAsc();
    List<PucAccountEntity> findByAccountClassAndActiveTrue(short accountClass);
    List<PucAccountEntity> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCodeAsc(
        String code, String name);
    Optional<PucAccountEntity> findByCode(String code);
    boolean existsByCode(String code);
    List<PucAccountEntity> findByParentCode(String parentCode);
    long countByParentCode(String parentCode);
}
