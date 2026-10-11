package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.SalePayment;
import co.posinvent.domain.repository.SalePaymentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class SalePaymentRepositoryAdapter implements SalePaymentRepository {

    private final SalePaymentJpaRepository jpa;
    private final SalePaymentMapper mapper;

    SalePaymentRepositoryAdapter(SalePaymentJpaRepository jpa, SalePaymentMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public SalePayment save(SalePayment payment) {
        var entity = mapper.toEntity(payment);
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public List<SalePayment> findBySalesDocumentId(UUID salesDocumentId) {
        return jpa.findBySalesDocumentId(salesDocumentId).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
