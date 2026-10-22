package co.posinvent.domain.repository;

import co.posinvent.domain.model.Tax;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxRepository {

    List<Tax> findAll();

    List<Tax> findAllActive();

    Optional<Tax> findById(UUID id);

    Optional<Tax> findByCode(String code);

    Tax save(Tax tax);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);
}
