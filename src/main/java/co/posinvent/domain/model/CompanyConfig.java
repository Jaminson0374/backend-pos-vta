package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CompanyConfig(
    Long id,
    String companyName,
    String nit,
    String address,
    String phone,
    String email,
    String economicActivity,
    String personType,
    String commonName,
    boolean manejaAiu,
    List<String> taxResponsibilityCodes,
    List<String> fiscalResponsibilityCodes,
    List<String> taxCodes,
    java.math.BigDecimal icaRate,
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
    java.util.UUID legalRepresentativeIdentificationTypeId,
    String legalRepresentativeDocumentNumber,
    String legalRepresentativeName,
    String legalRepresentativePosition,
    String legalRepresentativeAddress,
    String legalRepresentativeEmail,
    boolean autoGenerateJournalEntries,
    java.math.BigDecimal purchaseRetefuenteRate,
    java.math.BigDecimal purchaseIcaRate,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    BigDecimal maxDiscountPct,
    BigDecimal cashierMaxDiscountPct
) {

    public static final BigDecimal DEFAULT_MAX_DISCOUNT_PCT = new BigDecimal("30");
    public static final BigDecimal DEFAULT_CASHIER_MAX_DISCOUNT_PCT = new BigDecimal("5");

    public CompanyConfig {
        if (maxDiscountPct == null) maxDiscountPct = DEFAULT_MAX_DISCOUNT_PCT;
        if (cashierMaxDiscountPct == null) cashierMaxDiscountPct = DEFAULT_CASHIER_MAX_DISCOUNT_PCT;
        if (purchaseIcaRate == null) purchaseIcaRate = BigDecimal.ZERO;
    }

    /**
     * Backwards-compatible constructor: keeps existing callers compiling while
     * applying the default discount ceilings when none are supplied.
     */
    public CompanyConfig(
            Long id,
            String companyName,
            String nit,
            String address,
            String phone,
            String email,
            String economicActivity,
            String personType,
            String commonName,
            boolean manejaAiu,
            List<String> taxResponsibilityCodes,
            List<String> fiscalResponsibilityCodes,
            List<String> taxCodes,
            java.math.BigDecimal icaRate,
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
            java.util.UUID legalRepresentativeIdentificationTypeId,
            String legalRepresentativeDocumentNumber,
            String legalRepresentativeName,
            String legalRepresentativePosition,
            String legalRepresentativeAddress,
            String legalRepresentativeEmail,
            boolean autoGenerateJournalEntries,
            java.math.BigDecimal purchaseRetefuenteRate,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        this(id, companyName, nit, address, phone, email, economicActivity, personType, commonName,
                manejaAiu, taxResponsibilityCodes, fiscalResponsibilityCodes, taxCodes, icaRate,
                currency, mainWarehouseId, logoUrl, moratoryInterestRate, interestGraceDays,
                interestCompoundFrequency, costingMethod, overheadAllocationBase, overheadRate,
                dianResolutionId, softwarePin, certificateId,
                legalRepresentativeIdentificationTypeId, legalRepresentativeDocumentNumber,
                legalRepresentativeName, legalRepresentativePosition, legalRepresentativeAddress,
                legalRepresentativeEmail, autoGenerateJournalEntries, purchaseRetefuenteRate,
                BigDecimal.ZERO,
                createdAt, updatedAt,
                DEFAULT_MAX_DISCOUNT_PCT, DEFAULT_CASHIER_MAX_DISCOUNT_PCT);
    }
}
