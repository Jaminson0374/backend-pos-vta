package co.posinvent.domain.repository;

import co.posinvent.domain.model.TaxResponsibility;

import java.util.List;

public interface TaxResponsibilityRepository {

    List<TaxResponsibility> findAllActive();
}
