package co.posinvent.application.dto;

import co.posinvent.domain.model.CompanyConfig;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CompanyConfigResponse(
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
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static CompanyConfigResponse from(CompanyConfig c) {
        return new CompanyConfigResponse(
            c.id(),
            c.companyName(),
            c.nit(),
            c.address(),
            c.phone(),
            c.email(),
            c.economicActivity(),
            c.taxRegime(),
            c.currency(),
            c.mainWarehouseId(),
            c.logoUrl(),
            c.moratoryInterestRate(),
            c.interestGraceDays(),
            c.interestCompoundFrequency(),
            c.costingMethod(),
            c.overheadAllocationBase(),
            c.overheadRate(),
            c.dianResolutionId(),
            c.softwarePin(),
            c.certificateId(),
            c.autoGenerateJournalEntries(),
            c.createdAt(),
            c.updatedAt()
        );
    }
}
