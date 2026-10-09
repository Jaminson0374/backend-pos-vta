package co.posinvent.application.usecase;

import co.posinvent.application.dto.CiiuActivityResponse;
import co.posinvent.application.dto.CityResponse;
import co.posinvent.application.dto.DepartmentResponse;
import co.posinvent.application.dto.FiscalResponsibilityResponse;
import co.posinvent.application.dto.IdentificationTypeRequest;
import co.posinvent.application.dto.IdentificationTypeResponse;
import co.posinvent.application.dto.TaxResponse;
import co.posinvent.application.dto.TaxResponsibilityResponse;
import co.posinvent.domain.model.IdentificationType;
import co.posinvent.domain.repository.CiiuActivityRepository;
import co.posinvent.domain.repository.DepartmentRepository;
import co.posinvent.domain.repository.FiscalResponsibilityRepository;
import co.posinvent.domain.repository.IdentificationTypeRepository;
import co.posinvent.domain.repository.TaxRepository;
import co.posinvent.domain.repository.TaxResponsibilityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CatalogUseCase {

    private final IdentificationTypeRepository identificationTypeRepository;
    private final DepartmentRepository departmentRepository;
    private final CiiuActivityRepository ciiuActivityRepository;
    private final TaxResponsibilityRepository taxResponsibilityRepository;
    private final FiscalResponsibilityRepository fiscalResponsibilityRepository;
    private final TaxRepository taxRepository;

    public CatalogUseCase(
            IdentificationTypeRepository identificationTypeRepository,
            DepartmentRepository departmentRepository,
            CiiuActivityRepository ciiuActivityRepository,
            TaxResponsibilityRepository taxResponsibilityRepository,
            FiscalResponsibilityRepository fiscalResponsibilityRepository,
            TaxRepository taxRepository
    ) {
        this.identificationTypeRepository = identificationTypeRepository;
        this.departmentRepository = departmentRepository;
        this.ciiuActivityRepository = ciiuActivityRepository;
        this.taxResponsibilityRepository = taxResponsibilityRepository;
        this.fiscalResponsibilityRepository = fiscalResponsibilityRepository;
        this.taxRepository = taxRepository;
    }

    @Transactional(readOnly = true)
    public List<IdentificationTypeResponse> listIdentificationTypes() {
        return identificationTypeRepository.findAllActive().stream()
                .map(IdentificationTypeResponse::from)
                .toList();
    }

    @Transactional
    public IdentificationTypeResponse createIdentificationType(IdentificationTypeRequest request) {
        IdentificationType type = new IdentificationType(null, request.code(), request.name(), request.requiresDv(), true);
        return IdentificationTypeResponse.from(identificationTypeRepository.save(type));
    }

    @Transactional(readOnly = true)
    public List<DepartmentResponse> listDepartments() {
        return departmentRepository.findAll().stream()
                .map(DepartmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CityResponse> listCitiesByDepartment(UUID departmentId) {
        return departmentRepository.findCitiesByDepartmentId(departmentId).stream()
                .map(CityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CiiuActivityResponse> listCiiuActivities() {
        return ciiuActivityRepository.findAllActive().stream()
                .map(CiiuActivityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaxResponsibilityResponse> listTaxResponsibilities() {
        return taxResponsibilityRepository.findAllActive().stream()
                .map(TaxResponsibilityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FiscalResponsibilityResponse> listFiscalResponsibilities() {
        return fiscalResponsibilityRepository.findAllActive().stream()
                .map(FiscalResponsibilityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaxResponse> listTaxes() {
        return taxRepository.findAllActive().stream()
                .map(TaxResponse::from)
                .toList();
    }
}
