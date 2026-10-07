package co.posinvent.domain.repository;

import co.posinvent.domain.model.Tax;

import java.util.List;

public interface TaxRepository {

    List<Tax> findAllActive();
}
