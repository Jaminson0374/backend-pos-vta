package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.SupplierInvoice;
import co.posinvent.domain.repository.SupplierInvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class SupplierInvoiceRepositoryAdapter implements SupplierInvoiceRepository {

    private final SupplierInvoiceJpaRepository jpa;
    private final SupplierInvoiceMapper mapper;

    SupplierInvoiceRepositoryAdapter(SupplierInvoiceJpaRepository jpa, SupplierInvoiceMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public SupplierInvoice save(SupplierInvoice invoice) {
        var entity = mapper.toEntity(invoice);
        var saved = jpa.save(entity);
        return mapper.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplierInvoice> findById(UUID id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Page<SupplierInvoice> findAll(Pageable pageable) {
        return jpa.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public Page<SupplierInvoice> findBySupplierId(UUID supplierId, Pageable pageable) {
        return jpa.findBySupplierId(supplierId, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<SupplierInvoice> findByStatus(InvoiceStatus status, Pageable pageable) {
        return jpa.findByStatus(status, pageable).map(mapper::toDomain);
    }

    @Override
    public Page<SupplierInvoice> findBySupplierIdAndStatus(UUID supplierId, InvoiceStatus status,
                                                            Pageable pageable) {
        return jpa.findBySupplierIdAndStatus(supplierId, status, pageable).map(mapper::toDomain);
    }

    @Override
    public Optional<SupplierInvoice> findByInvoiceNumberAndSupplierId(String invoiceNumber,
                                                                       UUID supplierId) {
        return jpa.findByInvoiceNumberAndSupplierId(invoiceNumber, supplierId)
                .map(mapper::toDomain);
    }
}
