package co.posinvent.domain.repository;

import co.posinvent.domain.model.CiiuActivity;

import java.util.List;

public interface CiiuActivityRepository {

    List<CiiuActivity> findAllActive();
}
