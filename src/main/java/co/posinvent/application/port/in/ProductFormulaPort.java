package co.posinvent.application.port.in;

import co.posinvent.domain.model.ProductFormula;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ProductFormulaPort {

    List<ProductFormula> list(UUID productId);

    ProductFormula add(UUID productId, UUID componentProductId, BigDecimal quantity,
                       UUID unitOfMeasureId, int sequenceNumber, String notes);

    ProductFormula update(UUID id, BigDecimal quantity, UUID unitOfMeasureId,
                          int sequenceNumber, String notes);

    void remove(UUID id);
}
