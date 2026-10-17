package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Payment;
import co.posinvent.domain.repository.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpa;
    private final PaymentMapper mapper;

    PaymentRepositoryAdapter(PaymentJpaRepository jpa, PaymentMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Payment save(Payment payment) {
        var entity = mapper.toEntity(payment);
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<Payment> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Payment> findBySupplierId(UUID supplierId, Pageable pageable) {
        return jpa.findBySupplierId(supplierId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Payment> findByIsAdvanceTrue(Pageable pageable) {
        return jpa.findByIsAdvanceTrue(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<Payment> findByIsAdvanceTrueAndSupplierId(UUID supplierId, Pageable pageable) {
        return jpa.findByIsAdvanceTrueAndSupplierId(supplierId, pageable).map(mapper::toDomain);
    }

    @Override
    public BigDecimal sumRemainingAdvanceBySupplierId(UUID supplierId) {
        return jpa.sumRemainingAdvanceBySupplierId(supplierId);
    }
}
