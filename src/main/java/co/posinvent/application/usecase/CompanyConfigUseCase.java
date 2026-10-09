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
                null,
                null
        );

        var saved = repository.save(config);
        return CompanyConfigResponse.from(saved);
    }
}
