package co.posinvent.domain.repository;

import co.posinvent.domain.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(UUID id);

    Page<Payment> findAll(Pageable pageable);

    Page<Payment> findBySupplierId(UUID supplierId, Pageable pageable);

    Page<Payment> findByIsAdvanceTrue(Pageable pageable);

    Page<Payment> findByIsAdvanceTrueAndSupplierId(UUID supplierId, Pageable pageable);

    /**
     * Sums the unapplied balance of the supplier's advances (an asset, saldo a favor).
     *
     * @return the sum, or {@code null} when the supplier has no advances; callers normalize to zero
     */
    BigDecimal sumRemainingAdvanceBySupplierId(UUID supplierId);
}
