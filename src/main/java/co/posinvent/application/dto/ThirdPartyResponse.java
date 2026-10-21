package co.posinvent.application.dto;

import co.posinvent.domain.model.EmployeeBasicData;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.ThirdParty.PersonType;
import co.posinvent.domain.model.ThirdParty.TaxRegime;
import co.posinvent.domain.model.ThirdParty.ThirdPartyType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ThirdPartyResponse(
        UUID id,
        String numIdentification,
        String name,
        ThirdPartyType type,
        UUID priceListId,
        BigDecimal creditLimit,
        /**
         * Derived receivable balance: {@code SUM(accounts_receivable.outstanding)} for
         * {@code CLIENT}/{@code BOTH}; {@code null} for every other third-party type.
         * Never read from a static third-party balance column; the legacy column was removed in A6.
         */
        BigDecimal outstanding,
        PersonType personType,
        TaxRegime taxRegime,
        List<String> taxResponsibilities,
        String cityCode,
        String dianClassification,
        boolean active,
        OffsetDateTime createdAt,

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
    public static ThirdPartyResponse from(ThirdParty t, BigDecimal outstanding) {
        return new ThirdPartyResponse(
                t.id(), t.numIdentification(), t.name(), t.type(), t.priceListId(),
                t.creditLimit(), outstanding, t.personType(), t.taxRegime(),
                t.taxResponsibilities(), t.cityCode(), t.dianClassification(),
                t.active(), t.createdAt(),
                t.thirdPartyCategoryId(), t.identificationTypeId(),
                t.dv(), t.lastName(), t.commonName(), t.phone(), t.address(),
                t.departmentId(), t.cityId(), t.email(), t.website(), t.entryDate(), t.creditDays(),
                t.contactName(), t.contactPhone(), t.contactAddress(), t.contactEmail(),
                t.taxContactFirstName(), t.taxContactLastName(), t.taxEmail(), t.billingPhone(),
                t.isGranContribuyente(), t.isAutoretenedor(), t.isAgenteRetencionIva(),
                t.isRegimenSimple(), t.otherTaxResp(), t.employeeData(),
                t.dianIdentificationTypeCode(), t.dianTaxLevelCode(), t.municipalityCode(),
                t.cityName(), t.departmentCode(), t.departmentName(), t.postalCode(),
                t.countryCode(), t.registrationName(), t.additionalAccountId(),
                t.isConsumerFinal()
        );
    }
}
