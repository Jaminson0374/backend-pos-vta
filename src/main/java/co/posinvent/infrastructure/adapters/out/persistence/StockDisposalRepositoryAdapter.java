package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.StockDisposal;
import co.posinvent.domain.repository.StockDisposalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class StockDisposalRepositoryAdapter implements StockDisposalRepository {
    private final StockDisposalJpaRepository jpa;
    private final StockDisposalMapper mapper;
    public StockDisposalRepositoryAdapter(StockDisposalJpaRepository jpa, StockDisposalMapper mapper) { this.jpa = jpa; this.mapper = mapper; }

    @Override public StockDisposal save(StockDisposal d) { return mapper.toDomain(jpa.save(mapper.toEntity(d))); }
    @Override public Page<StockDisposal> findAll(Pageable p) { return jpa.findAll(p).map(mapper::toDomain); }

    @Override
    public List<Map<String, Object>> findExpiringBatches(int days) {
        return jpa.findExpiringBatchesNative(days);
    }

    @Override
    public List<Map<String, Object>> findExpiredBatches() {
        return jpa.findExpiredBatchesNative();
    }
}
