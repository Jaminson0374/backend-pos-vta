package co.posinvent.domain.repository;

import co.posinvent.domain.model.ThirdParty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThirdPartyRepository {

    ThirdParty save(ThirdParty thirdParty);

    Optional<ThirdParty> findById(UUID id);

    Page<ThirdParty> findAll(Pageable pageable);

    List<ThirdParty> findSuppliers();

    Page<ThirdParty> search(String query, Pageable pageable);

    boolean existsByNumIdentification(String numIdentification);

    boolean existsByNumIdentificationAndIdNot(String numIdentification, UUID id);

    Page<ThirdParty> findByTypeAndActive(ThirdParty.ThirdPartyType type, boolean active, Pageable pageable);

    List<ThirdParty> findEmployeesWithoutUser();

    Optional<ThirdParty> findByNumIdentification(String numIdentification);
}
