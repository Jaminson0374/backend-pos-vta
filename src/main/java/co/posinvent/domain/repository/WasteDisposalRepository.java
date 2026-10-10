package co.posinvent.domain.repository;

import co.posinvent.domain.model.WasteDisposal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface WasteDisposalRepository {

    WasteDisposal save(WasteDisposal disposal);

    Page<WasteDisposal> findAll(Pageable pageable);

    List<Map<String, Object>> findExpiringBatches(int days);

    List<Map<String, Object>> findExpiredBatches();
}
