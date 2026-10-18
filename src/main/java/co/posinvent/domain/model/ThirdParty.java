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
        EmployeeBasicData employeeData
) {

    public enum ThirdPartyType { CLIENT, SUPPLIER, BOTH, EMPLOYEE }

    public enum PersonType { NATURAL, JURIDICA }

    public enum TaxRegime { SIMPLE, ORDINARIO }
}
