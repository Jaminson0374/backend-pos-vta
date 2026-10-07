package co.posinvent.domain.repository;

import co.posinvent.domain.model.FiscalResponsibility;

import java.util.List;

public interface FiscalResponsibilityRepository {

    List<FiscalResponsibility> findAllActive();
}
