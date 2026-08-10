package co.posinvent.domain.repository;

import co.posinvent.domain.model.StockDisposal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface StockDisposalRepository {

    StockDisposal save(StockDisposal disposal);

    Page<StockDisposal> findAll(Pageable pageable);

    List<Map<String, Object>> findExpiringBatches(int days);

    List<Map<String, Object>> findExpiredBatches();
}
