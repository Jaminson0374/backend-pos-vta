package co.posinvent.infrastructure.adapters.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Spring Data projection for native query with LEFT JOINs that enrich batch
 * results with product, supplier, and warehouse names.
 */
interface BatchWithNamesProjection {

    UUID getId();

    UUID getProductId();

    UUID getSupplierId();

    UUID getWarehouseId();

    LocalDate getEntryDate();

    BigDecimal getInitialWeight();

    BigDecimal getPurchaseCost();

    String getStatus();

    String getNotes();

    LocalDate getExpirationDate();

    UUID getCreatedBy();

    Instant getCreatedAt();

    UUID getSourceReceiptId();

    UUID getOcId();

    UUID getParentBatchId();

    String getBatchType();

    UUID getUnitOfMeasureId();

    String getProductName();

    String getSupplierName();

    String getWarehouseName();
}
