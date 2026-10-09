package co.posinvent.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CompanyConfigRequest(
    @NotBlank @Size(max = 255) String companyName,
    @NotBlank @Size(max = 20) String nit,
    @Size(max = 255) String address,
    @Size(max = 30) String phone,
    @Email @Size(max = 255) String email,
    @Size(max = 255) String economicActivity,
    @Size(max = 10) String personType,
    @Size(max = 200) String commonName,
    Boolean manejaAiu,
    List<String> taxResponsibilityCodes,
    List<String> fiscalResponsibilityCodes,
    List<String> taxCodes,
    java.math.BigDecimal icaRate,
    @NotBlank @Size(max = 3) String currency,
    UUID mainWarehouseId,
    @Size(max = 500) String logoUrl,
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
    @Size(max = 40) String legalRepresentativeDocumentNumber,
    @Size(max = 200) String legalRepresentativeName,
    @Size(max = 100) String legalRepresentativePosition,
    @Size(max = 255) String legalRepresentativeAddress,
    @Email @Size(max = 255) String legalRepresentativeEmail,
    java.math.BigDecimal purchaseRetefuenteRate,
    Boolean autoGenerateJournalEntries
) {}
