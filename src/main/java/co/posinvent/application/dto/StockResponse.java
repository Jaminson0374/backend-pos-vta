package co.posinvent.application.dto;

import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.Product;

import java.math.BigDecimal;
import java.util.UUID;

public record StockResponse(
        UUID id,
        UUID productId,
        UUID batchId,
        UUID warehouseId,
        BigDecimal currentQuantity,
        BigDecimal committedQuantity,
        BigDecimal availableQuantity,
        BigDecimal unitCost,
        String productName,
        String productCode,
        String batchType
) {
    public static StockResponse from(InventoryStock s) {
        return new StockResponse(
                s.id(), s.productId(), s.batchId(), s.warehouseId(),
                s.currentQuantity(), s.committedQuantity(),
                s.availableQuantity(), s.unitCost(),
                null, null, null
        );
    }

    public static StockResponse enriched(InventoryStock s, Product product, Batch batch) {
        return new StockResponse(
                s.id(), s.productId(), s.batchId(), s.warehouseId(),
                s.currentQuantity(), s.committedQuantity(),
                s.availableQuantity(), s.unitCost(),
                product != null ? product.name() : null,
                product != null ? product.productCode() : null,
                batch != null ? (batch.batchType() != null ? batch.batchType().name() : null) : null
        );
    }
}