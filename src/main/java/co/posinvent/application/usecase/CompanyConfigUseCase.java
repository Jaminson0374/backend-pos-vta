package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.CompanyConfigRequest;
import co.posinvent.application.dto.CompanyConfigResponse;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.CompanyConfig;
import co.posinvent.domain.repository.CompanyConfigRepository;
import co.posinvent.domain.repository.WarehouseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyConfigUseCase {

    private final CompanyConfigRepository repository;
    private final WarehouseRepository warehouseRepository;

    public CompanyConfigUseCase(
            CompanyConfigRepository repository,
            WarehouseRepository warehouseRepository
    ) {
        this.repository = repository;
        this.warehouseRepository = warehouseRepository;
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

        var config = new CompanyConfig(
                1L,
                request.companyName(),
                request.nit(),
                request.address(),
                request.phone(),
                request.email(),
                request.economicActivity(),
                request.taxRegime(),
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
                request.autoGenerateJournalEntries() != null ? request.autoGenerateJournalEntries() : true,
                null,
                null
        );

        var saved = repository.save(config);
        return CompanyConfigResponse.from(saved);
    }
}
