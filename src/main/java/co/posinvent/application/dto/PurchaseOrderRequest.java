package co.posinvent.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderRequest(
        @NotNull UUID supplierId,
        @NotNull LocalDate orderDate,
        @Size(max = 500) String notes,
        LocalDate dueDate,
        UUID buyerId,
        @Size(max = 50) String paymentMethod,
        @Size(max = 50) String supportDocumentType,
        @Size(max = 50) String supportDocumentNumber,
        @Size(max = 10) String currency,
        @NotNull @NotEmpty @Valid List<LineItem> lines
) {
    public record LineItem(
            @NotNull UUID productId,
            @NotNull @DecimalMin("0.001") BigDecimal orderedQty,
            @NotNull @DecimalMin("0") BigDecimal unitCost,
            @NotNull UUID warehouseId
    ) {}
}
