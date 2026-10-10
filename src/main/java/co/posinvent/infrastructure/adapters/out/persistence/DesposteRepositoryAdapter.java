package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Desposte;
import co.posinvent.domain.repository.DesposteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DesposteRepositoryAdapter implements DesposteRepository {

    private final DesposteJpaRepository jpa;
    private final DesposteMapper mapper;

    public DesposteRepositoryAdapter(DesposteJpaRepository jpa, DesposteMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Desposte save(Desposte desposte) {
        DesposteEntity entity;
        if (desposte.id() != null) {
            entity = jpa.findById(desposte.id())
                    .orElseThrow(() -> new ResourceNotFoundException("Desposte", desposte.id()));
            mapper.updateEntity(entity, desposte);
        } else {
            entity = mapper.toEntity(desposte);
        }
        if (desposte.cuts() != null) {
            var cutEntities = new ArrayList<DesposteCutEntity>();
            for (var cut : desposte.cuts()) {
                var cutEntity = mapper.toCutEntity(cut);
                cutEntity.setDesposte(entity);
                cutEntities.add(cutEntity);
            }
            entity.getCuts().clear();
            entity.getCuts().addAll(cutEntities);
        }
        return mapper.toDomain(jpa.save(entity));
    }

    @Override
    public Optional<Desposte> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<Desposte> findPage(LocalDate from, LocalDate to, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        OffsetDateTime start = from == null
                ? null
                : from.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime endExclusive = to == null
                ? null
                : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();

        Page<DesposteEntity> result;
        if (start != null && endExclusive != null) {
            result = jpa.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(start, endExclusive, pageable);
        } else if (start != null) {
            result = jpa.findByCreatedAtGreaterThanEqual(start, pageable);
        } else if (endExclusive != null) {
            result = jpa.findByCreatedAtLessThan(endExclusive, pageable);
        } else {
            result = jpa.findAll(pageable);
        }
        return result.map(mapper::toDomain);
    }
}
