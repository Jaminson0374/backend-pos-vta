package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.WasteDisposal;
import co.posinvent.domain.repository.WasteDisposalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class WasteDisposalRepositoryAdapter implements WasteDisposalRepository {
    private final WasteDisposalJpaRepository jpa;
    private final WasteDisposalMapper mapper;
    public WasteDisposalRepositoryAdapter(WasteDisposalJpaRepository jpa, WasteDisposalMapper mapper) { this.jpa = jpa; this.mapper = mapper; }

    @Override public WasteDisposal save(WasteDisposal d) {
        WasteDisposalEntity entity;
        if (d.id() != null) {
            entity = jpa.findById(d.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Baja de stock", d.id()));
            mapper.updateEntity(entity, d);
        } else {
            entity = mapper.toEntity(d);
        }
        return mapper.toDomain(jpa.save(entity));
    }
    @Override public Page<WasteDisposal> findAll(Pageable p) { return jpa.findAll(p).map(mapper::toDomain); }

    @Override
    public List<Map<String, Object>> findExpiringBatches(int days) {
        return jpa.findExpiringBatchesNative(days);
    }

    @Override
    public List<Map<String, Object>> findExpiredBatches() {
        return jpa.findExpiredBatchesNative();
    }
}
