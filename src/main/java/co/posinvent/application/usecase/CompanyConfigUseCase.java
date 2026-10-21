package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.CompanyConfigRequest;
import co.posinvent.application.dto.CompanyConfigResponse;
import co.posinvent.application.service.TaxSelectionValidator;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.FiscalResponsibilityRepository;
import co.posinvent.domain.repository.IdentificationTypeRepository;
import co.posinvent.domain.repository.TaxResponsibilityRepository;
import co.posinvent.domain.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanyConfigUseCase {

    private final CompanyConfigRepository repository;
    private final WarehouseRepository warehouseRepository;
    private final IdentificationTypeRepository identificationTypeRepository;
    private final TaxResponsibilityRepository taxResponsibilityRepository;
    private final FiscalResponsibilityRepository fiscalResponsibilityRepository;
    private final TaxSelectionValidator validator;

    public CompanyConfigUseCase(
            CompanyConfigRepository repository,
            WarehouseRepository warehouseRepository,
            IdentificationTypeRepository identificationTypeRepository,
            TaxResponsibilityRepository taxResponsibilityRepository,
            FiscalResponsibilityRepository fiscalResponsibilityRepository,
            TaxSelectionValidator validator
    ) {
        this.repository = repository;
        this.warehouseRepository = warehouseRepository;
        this.identificationTypeRepository = identificationTypeRepository;
        this.taxResponsibilityRepository = taxResponsibilityRepository;
        this.fiscalResponsibilityRepository = fiscalResponsibilityRepository;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public CompanyConfigResponse getConfig() {
        return repository.findConfig()
                .map(CompanyConfigResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Configuración de empresa", 1L));
    }

    @Auditable(entityType = "COMPANY_CONFIG", action = "UPDATE")
    @Transactional
    public CompanyConfigResponse saveConfig(CompanyConfigRequest request) {
        if (request.mainWarehouseId() != null) {
            warehouseRepository.findById(request.mainWarehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bodega", request.mainWarehouseId()));
        }

        if (request.legalRepresentativeIdentificationTypeId() != null) {
            identificationTypeRepository.findById(request.legalRepresentativeIdentificationTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tipo de identificación", request.legalRepresentativeIdentificationTypeId()));
        }

        validator.validateTaxResponsibilities(
                taxResponsibilityRepository.findAllActive(),
                request.taxResponsibilityCodes());
        validator.validateFiscalResponsibilities(
                fiscalResponsibilityRepository.findAllActive(),
                request.fiscalResponsibilityCodes());

        // Discount ceilings are administrative policy, not part of the everyday company
        // form. Preserve the currently persisted values when the request omits them so a
        // routine config save never resets the ceilings back to their defaults.
        var existing = repository.findConfig().orElse(null);
        var maxDiscountPct = request.maxDiscountPct() != null
                ? request.maxDiscountPct()
                : (existing == null ? null : existing.maxDiscountPct());
        var cashierMaxDiscountPct = request.cashierMaxDiscountPct() != null
                ? request.cashierMaxDiscountPct()
                : (existing == null ? null : existing.cashierMaxDiscountPct());

        var config = new CompanyConfig(
                1L,
                request.companyName(),
                request.nit(),
                request.address(),
                request.phone(),
                request.email(),
                request.economicActivity(),
                request.personType(),
                request.commonName(),
                request.manejaAiu() != null && request.manejaAiu(),
                request.taxResponsibilityCodes() != null ? request.taxResponsibilityCodes() : List.of(),
                request.fiscalResponsibilityCodes() != null ? request.fiscalResponsibilityCodes() : List.of(),
                request.taxCodes() != null ? request.taxCodes() : List.of(),
                request.icaRate(),
                request.currency(),
                request.mainWarehouseId(),
                request.logoUrl(),
                request.moratoryInterestRate(),
                request.interestGraceDays(),
                request.interestCompoundFrequency(),
                request.costingMethod(),
                request.overheadAllocationBase(),
                request.overheadRate(),
                request.dianResolutionId(),
                request.softwarePin(),
                request.certificateId(),
                request.legalRepresentativeIdentificationTypeId(),
                request.legalRepresentativeDocumentNumber(),
                request.legalRepresentativeName(),
                request.legalRepresentativePosition(),
                request.legalRepresentativeAddress(),
                request.legalRepresentativeEmail(),
                request.autoGenerateJournalEntries() != null ? request.autoGenerateJournalEntries() : true,
                request.purchaseRetefuenteRate(),
                request.purchaseIcaRate(),
                null,
                null,
                maxDiscountPct,
                cashierMaxDiscountPct,
                valueOr(request.dianIdentificationTypeCode(),
                        existing == null ? null : existing.dianIdentificationTypeCode()),
                valueOr(request.dv(), existing == null ? null : existing.dv()),
                valueOr(request.municipalityCode(), existing == null ? null : existing.municipalityCode()),
                valueOr(request.cityName(), existing == null ? null : existing.cityName()),
                valueOr(request.departmentCode(), existing == null ? null : existing.departmentCode()),
                valueOr(request.departmentName(), existing == null ? null : existing.departmentName()),
                valueOr(request.postalCode(), existing == null ? null : existing.postalCode()),
                valueOr(request.countryCode(), existing == null ? null : existing.countryCode()),
                valueOr(request.taxLevelCode(), existing == null ? null : existing.taxLevelCode()),
                valueOr(request.dianEnvironment(), existing == null ? null : existing.dianEnvironment())
        );

        var saved = repository.save(config);
        return CompanyConfigResponse.from(saved);
    }

    private static <T> T valueOr(T requested, T existing) {
        return requested != null ? requested : existing;
    }
}
