package co.posinvent.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CompanyConfig(
    Long id,
    String companyName,
    String nit,
    String address,
    String phone,
    String email,
    String economicActivity,
    String taxRegime,
    String currency,
    UUID mainWarehouseId,
    String logoUrl,
    java.math.BigDecimal moratoryInterestRate,
    Integer interestGraceDays,
    String interestCompoundFrequency,
    String costingMethod,
    String overheadAllocationBase,
    java.math.BigDecimal overheadRate,
    java.util.UUID dianResolutionId,
    String softwarePin,
    java.util.UUID certificateId,
    boolean autoGenerateJournalEntries,
    java.math.BigDecimal purchaseRetefuenteRate,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
