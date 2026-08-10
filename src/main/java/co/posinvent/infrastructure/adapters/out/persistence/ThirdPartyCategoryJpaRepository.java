package co.posinvent.infrastructure.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ThirdPartyCategoryJpaRepository extends JpaRepository<ThirdPartyCategoryEntity, UUID> {

    List<ThirdPartyCategoryEntity> findByActiveTrue();
}
