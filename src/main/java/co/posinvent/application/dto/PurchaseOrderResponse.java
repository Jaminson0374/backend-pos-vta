package co.posinvent.application.dto;

import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseLineItem;
import co.posinvent.domain.model.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        UUID supplierId,
        String supplierName,
        PurchaseOrderStatus status,
        LocalDate orderDate,
        String documentNumber,
        String notes,
        LocalDate dueDate,
        UUID buyerId,
        String buyerName,
        String paymentMethod,
        String supportDocumentType,
        String supportDocumentNumber,
        String currency,
        UUID createdBy,
        OffsetDateTime createdAt,
        List<LineItemResponse> lines,
        BigDecimal taxTotal,
        BigDecimal discountTotal,
        BigDecimal grandTotal
) {
    private static final Map<String, BigDecimal> TAX_RATES = Map.of(
            "EXENTO", BigDecimal.ZERO,
            "IVA_5", new BigDecimal("5"),
            "IVA_8", new BigDecimal("8"),
            "IVA_19", new BigDecimal("19")
    );

    public record LineItemResponse(
            UUID id,
            UUID productId,
            String productName,
            BigDecimal orderedQty,
            BigDecimal receivedQty,
            BigDecimal unitCost,
            UUID warehouseId,
            String warehouseName,
            BigDecimal discountPct,
            String taxType,
            int lineNumber
    ) {}

    public static PurchaseOrderResponse from(PurchaseOrder po) {
        var lines = po.lines().stream()
                .map(PurchaseOrderResponse::fromLineItem)
                .toList();

        var discountTotal = BigDecimal.ZERO;
        var taxTotal = BigDecimal.ZERO;
        var grandTotal = BigDecimal.ZERO;

        for (var line : po.lines()) {
            var subtotal = line.orderedQty().multiply(line.unitCost());
            var discPct = line.discountPct() != null ? line.discountPct() : BigDecimal.ZERO;
            var discountAmount = subtotal.multiply(discPct)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            var afterDiscount = subtotal.subtract(discountAmount);
            var taxRate = TAX_RATES.getOrDefault(line.taxType(), BigDecimal.ZERO);
            var taxAmount = afterDiscount.multiply(taxRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            var lineTotal = afterDiscount.add(taxAmount);

            discountTotal = discountTotal.add(discountAmount);
            taxTotal = taxTotal.add(taxAmount);
            grandTotal = grandTotal.add(lineTotal);
        }

        return new PurchaseOrderResponse(
                po.id(),
                po.supplierId(),
                null,
                po.status(),
                po.orderDate(),
                po.documentNumber(),
                po.notes(),
                po.dueDate(),
                po.buyerId(),
                null,
                po.paymentMethod(),
                po.supportDocumentType(),
                po.supportDocumentNumber(),
                po.currency(),
                po.createdBy(),
                po.createdAt(),
                lines,
                taxTotal,
                discountTotal,
                grandTotal
        );
    }

    private static LineItemResponse fromLineItem(PurchaseLineItem li) {
        return new LineItemResponse(
                li.id(),
                li.productId(),
                null,
                li.orderedQty(),
                li.receivedQty(),
                li.unitCost(),
                li.warehouseId(),
                null,
                li.discountPct(),
                li.taxType(),
                li.lineNumber()
        );
    }
}
