package co.posinvent.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ThirdParty(
        UUID id,
        String numIdentification,
        String name,
        ThirdPartyType type,
        UUID priceListId,
        BigDecimal creditLimit,
        PersonType personType,
        TaxRegime taxRegime,
        List<String> taxResponsibilities,
        String cityCode,
        String dianClassification,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,

        // New fields
        UUID thirdPartyCategoryId,
        UUID identificationTypeId,
        String dv,
        String lastName,
        String commonName,
        String phone,
        String address,
        UUID departmentId,
        UUID cityId,
        String email,
        String website,
        LocalDate entryDate,
        int creditDays,
        String contactName,
        String contactPhone,
        String contactAddress,
        String contactEmail,
        String taxContactFirstName,
        String taxContactLastName,
        String taxEmail,
        String billingPhone,
        boolean isGranContribuyente,
        boolean isAutoretenedor,
        boolean isAgenteRetencionIva,
        boolean isRegimenSimple,
        boolean otherTaxResp,
        EmployeeBasicData employeeData,
        String dianIdentificationTypeCode,
        String dianTaxLevelCode,
        String municipalityCode,
        String cityName,
        String departmentCode,
        String departmentName,
        String postalCode,
        String countryCode,
        String registrationName,
        String additionalAccountId,
        boolean isConsumerFinal
) {

    public enum ThirdPartyType { CLIENT, SUPPLIER, BOTH, EMPLOYEE }

    public enum PersonType { NATURAL, JURIDICA }

    public enum TaxRegime { SIMPLE, ORDINARIO }

    public ThirdParty {
        if (countryCode == null || countryCode.isBlank()) countryCode = "CO";
        if (registrationName == null || registrationName.isBlank()) registrationName = name;
        if (isConsumerFinal) {
            if (dianIdentificationTypeCode == null || dianIdentificationTypeCode.isBlank()) {
                dianIdentificationTypeCode = "13";
            }
            if (dianTaxLevelCode == null || dianTaxLevelCode.isBlank()) {
                dianTaxLevelCode = "R-99-PN";
            }
        }
        if (additionalAccountId == null && personType != null) {
            additionalAccountId = personType == PersonType.JURIDICA ? "1" : "2";
        }
    }

    /** Backwards-compatible constructor without D2 fiscal fields. */
    public ThirdParty(
            UUID id,
            String numIdentification,
            String name,
            ThirdPartyType type,
            UUID priceListId,
            BigDecimal creditLimit,
            PersonType personType,
            TaxRegime taxRegime,
            List<String> taxResponsibilities,
            String cityCode,
            String dianClassification,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            UUID thirdPartyCategoryId,
            UUID identificationTypeId,
            String dv,
            String lastName,
            String commonName,
            String phone,
            String address,
            UUID departmentId,
            UUID cityId,
            String email,
            String website,
            LocalDate entryDate,
            int creditDays,
            String contactName,
            String contactPhone,
            String contactAddress,
            String contactEmail,
            String taxContactFirstName,
            String taxContactLastName,
            String taxEmail,
            String billingPhone,
            boolean isGranContribuyente,
            boolean isAutoretenedor,
            boolean isAgenteRetencionIva,
            boolean isRegimenSimple,
            boolean otherTaxResp,
            EmployeeBasicData employeeData
    ) {
        this(id, numIdentification, name, type, priceListId, creditLimit, personType, taxRegime,
                taxResponsibilities, cityCode, dianClassification, active, createdAt, updatedAt,
                thirdPartyCategoryId, identificationTypeId, dv, lastName, commonName, phone, address,
                departmentId, cityId, email, website, entryDate, creditDays, contactName,
                contactPhone, contactAddress, contactEmail, taxContactFirstName, taxContactLastName,
                taxEmail, billingPhone, isGranContribuyente, isAutoretenedor, isAgenteRetencionIva,
                isRegimenSimple, otherTaxResp, employeeData,
                null, null, null, null, null, null, null, null, null, null, false);
    }
}
