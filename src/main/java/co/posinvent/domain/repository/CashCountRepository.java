package co.posinvent.domain.repository;

import co.posinvent.domain.model.CashCount;

import java.util.List;
import java.util.UUID;

public interface CashCountRepository {

    CashCount save(CashCount cashCount);

    List<CashCount> findByShiftId(UUID shiftId);
}
