package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.EmployeeBasicDataRequest;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.ThirdPartyRequest;
import co.posinvent.application.dto.ThirdPartyResponse;
import co.posinvent.application.dto.ThirdPartySupplierOptionResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.EmployeeBasicData;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.ThirdParty.ThirdPartyType;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ThirdPartyUseCase {

    private final ThirdPartyRepository thirdPartyRepository;

    public ThirdPartyUseCase(ThirdPartyRepository thirdPartyRepository) {
        this.thirdPartyRepository = thirdPartyRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ThirdPartyResponse> list(Pageable pageable) {
        return PageResponse.from(thirdPartyRepository.findAll(pageable), ThirdPartyResponse::from);
    }

    @Transactional(readOnly = true)
    public List<ThirdPartySupplierOptionResponse> listSupplierOptions() {
        return thirdPartyRepository.findSuppliers().stream()
                .map(ThirdPartySupplierOptionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ThirdPartyResponse> search(String query, Pageable pageable) {
        var searchPattern = query != null && !query.isBlank() ? "%" + query.toLowerCase() + "%" : null;
        return PageResponse.from(thirdPartyRepository.search(searchPattern, pageable), ThirdPartyResponse::from);
    }

    @Transactional(readOnly = true)
    public ThirdPartyResponse getById(UUID id) {
        return thirdPartyRepository.findById(id)
                .map(ThirdPartyResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Tercero", id));
    }

    @Auditable(entityType = "THIRD_PARTY", action = "CREATE")
    @Transactional
    public ThirdPartyResponse create(ThirdPartyRequest request) {
        if (thirdPartyRepository.existsByNumIdentification(request.numIdentification())) {
            throw new BusinessException("DUPLICATE_NUM_IDENTIFICATION",
                    "Ya existe un tercero con el NIT/CC: " + request.numIdentification());
        }

        var thirdParty = new ThirdParty(
                null,
                request.numIdentification(),
                request.name(),
                resolveType(request),
                request.priceListId(),
                request.creditLimit(),
                BigDecimal.ZERO,
                request.personType(),
                request.taxRegime(),
                request.taxResponsibilities(),
                request.cityCode(),
                request.dianClassification(),
                true,
                null,
                null,
                request.thirdPartyCategoryId(),
                request.identificationTypeId(),
                request.dv(),
                request.lastName(),
                request.commonName(),
                request.phone(),
                request.address(),
                request.departmentId(),
                request.cityId(),
                request.email(),
                request.website(),
                request.entryDate(),
                request.creditDays(),
                request.contactName(),
                request.contactPhone(),
                request.contactAddress(),
                request.contactEmail(),
                request.taxContactFirstName(),
                request.taxContactLastName(),
                request.taxEmail(),
                request.billingPhone(),
                request.isGranContribuyente(),
                request.isAutoretenedor(),
                request.isAgenteRetencionIva(),
                request.isRegimenSimple(),
                request.otherTaxResp(),
                toEmployeeData(request.employeeData())
        );

        return ThirdPartyResponse.from(thirdPartyRepository.save(thirdParty));
    }

    @Auditable(entityType = "THIRD_PARTY", action = "UPDATE")
    @Transactional
    public ThirdPartyResponse update(UUID id, ThirdPartyRequest request) {
        var existing = thirdPartyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tercero", id));

        if (thirdPartyRepository.existsByNumIdentificationAndIdNot(request.numIdentification(), id)) {
            throw new BusinessException("DUPLICATE_NUM_IDENTIFICATION",
                    "Ya existe un tercero con el NIT/CC: " + request.numIdentification());
        }

        var updated = new ThirdParty(
                existing.id(),
                request.numIdentification(),
                request.name(),
                resolveType(request),
                request.priceListId(),
                request.creditLimit(),
                existing.currentBalance(),
                request.personType(),
                request.taxRegime(),
                request.taxResponsibilities(),
                request.cityCode(),
                request.dianClassification(),
                existing.active(),
                existing.createdAt(),
                null,
                request.thirdPartyCategoryId(),
                request.identificationTypeId(),
                request.dv(),
                request.lastName(),
                request.commonName(),
                request.phone(),
                request.address(),
                request.departmentId(),
                request.cityId(),
                request.email(),
                request.website(),
                request.entryDate(),
                request.creditDays(),
                request.contactName(),
                request.contactPhone(),
                request.contactAddress(),
                request.contactEmail(),
                request.taxContactFirstName(),
                request.taxContactLastName(),
                request.taxEmail(),
                request.billingPhone(),
                request.isGranContribuyente(),
                request.isAutoretenedor(),
                request.isAgenteRetencionIva(),
                request.isRegimenSimple(),
                request.otherTaxResp(),
                toEmployeeData(request.employeeData())
        );

        return ThirdPartyResponse.from(thirdPartyRepository.save(updated));
    }

    private ThirdPartyType resolveType(ThirdPartyRequest request) {
        return request.type() != null ? request.type() : ThirdPartyType.CLIENT;
    }

    private EmployeeBasicData toEmployeeData(EmployeeBasicDataRequest r) {
        if (r == null) return null;
        return new EmployeeBasicData(
                null,
                r.position(), r.costCenter(), r.workCenter(),
                r.gender(), r.civilStatus(), r.salesGroup(),
                r.birthDate(), r.birthPlace(), r.militaryId(),
                r.isForeigner(), r.natResidentExterior(), r.isDeclarant(),
                r.associatedSeller(), r.requiresEndowment(), r.isSenior()
        );
    }
}
