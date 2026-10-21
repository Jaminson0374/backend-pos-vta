package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.EmployeeBasicDataRequest;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.ThirdPartyRequest;
import co.posinvent.application.dto.ThirdPartyResponse;
import co.posinvent.application.dto.ThirdPartySupplierOptionResponse;
import co.posinvent.application.dto.ThirdPartySummaryResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.EmployeeBasicData;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.ThirdParty.ThirdPartyType;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.infrastructure.adapters.out.persistence.ThirdPartyCategoryJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ThirdPartyUseCase {

    private final ThirdPartyRepository thirdPartyRepository;
    private final AccountsReceivableRepository accountsReceivableRepository;
    private final ThirdPartyCategoryJpaRepository categoryJpaRepository;

    public ThirdPartyUseCase(
            ThirdPartyRepository thirdPartyRepository,
            AccountsReceivableRepository accountsReceivableRepository,
            ThirdPartyCategoryJpaRepository categoryJpaRepository) {
        this.thirdPartyRepository = thirdPartyRepository;
        this.accountsReceivableRepository = accountsReceivableRepository;
        this.categoryJpaRepository = categoryJpaRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ThirdPartyResponse> list(Pageable pageable) {
        return toPageResponse(thirdPartyRepository.findAll(pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<ThirdPartyResponse> listByTypeAndActive(ThirdPartyType type, boolean active, Pageable pageable) {
        return toPageResponse(thirdPartyRepository.findByTypeAndActive(type, active, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<ThirdPartyResponse> listByPersonTypeAndActive(ThirdParty.PersonType personType, boolean active, Pageable pageable) {
        return toPageResponse(thirdPartyRepository.findByPersonTypeAndActive(personType, active, pageable));
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
        return toPageResponse(thirdPartyRepository.search(searchPattern, pageable));
    }

    @Transactional(readOnly = true)
    public ThirdPartyResponse getById(UUID id) {
        return thirdPartyRepository.findById(id)
                .map(this::toResponse)
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
                toEmployeeData(request.employeeData()),
                request.dianIdentificationTypeCode(),
                request.dianTaxLevelCode(),
                request.municipalityCode(),
                request.cityName(),
                request.departmentCode(),
                request.departmentName(),
                request.postalCode(),
                request.countryCode(),
                request.registrationName(),
                request.additionalAccountId(),
                Boolean.TRUE.equals(request.isConsumerFinal())
        );

        return toResponse(thirdPartyRepository.save(thirdParty));
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
                toEmployeeData(request.employeeData()),
                valueOr(request.dianIdentificationTypeCode(), existing.dianIdentificationTypeCode()),
                valueOr(request.dianTaxLevelCode(), existing.dianTaxLevelCode()),
                valueOr(request.municipalityCode(), existing.municipalityCode()),
                valueOr(request.cityName(), existing.cityName()),
                valueOr(request.departmentCode(), existing.departmentCode()),
                valueOr(request.departmentName(), existing.departmentName()),
                valueOr(request.postalCode(), existing.postalCode()),
                valueOr(request.countryCode(), existing.countryCode()),
                valueOr(request.registrationName(), existing.registrationName()),
                valueOr(request.additionalAccountId(), existing.additionalAccountId()),
                request.isConsumerFinal() != null ? request.isConsumerFinal() : existing.isConsumerFinal()
        );

        return toResponse(thirdPartyRepository.save(updated));
    }

    @Transactional(readOnly = true)
    public List<ThirdPartySummaryResponse> findEmployeesWithoutUser() {
        return thirdPartyRepository.findEmployeesWithoutUser().stream()
                .map(ThirdPartySummaryResponse::from)
                .toList();
    }

    private PageResponse<ThirdPartyResponse> toPageResponse(Page<ThirdParty> page) {
        var clientIds = page.getContent().stream()
                .filter(ThirdPartyUseCase::isClient)
                .map(ThirdParty::id)
                .filter(Objects::nonNull)
                .toList();
        var sums = clientIds.isEmpty()
                ? Map.<UUID, BigDecimal>of()
                : accountsReceivableRepository.sumOutstandingByClientIds(clientIds);
        return PageResponse.from(page, t -> ThirdPartyResponse.from(t, outstandingFor(t, sums)));
    }

    private ThirdPartyResponse toResponse(ThirdParty t) {
        return ThirdPartyResponse.from(t,
                isClient(t) ? accountsReceivableRepository.sumOutstandingByClientId(t.id()) : null);
    }

    private static boolean isClient(ThirdParty t) {
        return t.type() == ThirdPartyType.CLIENT || t.type() == ThirdPartyType.BOTH;
    }

    private static BigDecimal outstandingFor(ThirdParty t, Map<UUID, BigDecimal> sums) {
        return isClient(t) ? sums.getOrDefault(t.id(), BigDecimal.ZERO) : null;
    }

    private ThirdPartyType resolveType(ThirdPartyRequest request) {
        // 1. Explicit type from request takes priority
        if (request.type() != null) return request.type();
        // 2. Resolve from category's baseType
        if (request.thirdPartyCategoryId() != null) {
            return categoryJpaRepository.findById(request.thirdPartyCategoryId())
                    .map(cat -> {
                        try {
                            return ThirdPartyType.valueOf(cat.getBaseType());
                        } catch (IllegalArgumentException e) {
                            return ThirdPartyType.CLIENT;
                        }
                    })
                    .orElse(ThirdPartyType.CLIENT);
        }
        // 3. Fallback
        return ThirdPartyType.CLIENT;
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

    private static <T> T valueOr(T requested, T existing) {
        return requested != null ? requested : existing;
    }
}
