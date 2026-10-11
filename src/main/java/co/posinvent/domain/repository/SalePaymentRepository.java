package co.posinvent.domain.repository;

import co.posinvent.domain.model.SalePayment;

import java.util.List;
import java.util.UUID;

public interface SalePaymentRepository {

    SalePayment save(SalePayment payment);

    List<SalePayment> findBySalesDocumentId(UUID salesDocumentId);
}
