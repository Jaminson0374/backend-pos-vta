package co.posinvent.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SupplierPurchaseResponse(
        UUID supplierId,
        String supplierName,
        BigDecimal totalPurchased,
        int orderCount,
        BigDecimal avgOrderValue
) {}
