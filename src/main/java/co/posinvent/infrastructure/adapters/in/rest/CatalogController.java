package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.CiiuActivityResponse;
import co.posinvent.application.dto.CityResponse;
import co.posinvent.application.dto.DepartmentResponse;
import co.posinvent.application.dto.FiscalResponsibilityResponse;
import co.posinvent.application.dto.IdentificationTypeRequest;
import co.posinvent.application.dto.IdentificationTypeResponse;
import co.posinvent.application.dto.TaxResponse;
import co.posinvent.application.dto.TaxResponsibilityResponse;
import co.posinvent.application.usecase.CatalogUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {

    private final CatalogUseCase catalogUseCase;

    public CatalogController(CatalogUseCase catalogUseCase) {
        this.catalogUseCase = catalogUseCase;
    }

    @GetMapping("/identification-types")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<List<IdentificationTypeResponse>> listIdentificationTypes() {
        return ResponseEntity.ok(catalogUseCase.listIdentificationTypes());
    }

    @PostMapping("/identification-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IdentificationTypeResponse> createIdentificationType(
            @Valid @RequestBody IdentificationTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogUseCase.createIdentificationType(request));
    }

    @GetMapping("/departments")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<List<DepartmentResponse>> listDepartments() {
        return ResponseEntity.ok(catalogUseCase.listDepartments());
    }

    @GetMapping("/departments/{id}/cities")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<List<CityResponse>> listCitiesByDepartment(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogUseCase.listCitiesByDepartment(id));
    }

    @GetMapping("/ciiu-activities")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<List<CiiuActivityResponse>> listCiiuActivities() {
        return ResponseEntity.ok(catalogUseCase.listCiiuActivities());
    }

    @GetMapping("/tax-responsibilities")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<List<TaxResponsibilityResponse>> listTaxResponsibilities() {
        return ResponseEntity.ok(catalogUseCase.listTaxResponsibilities());
    }

    @GetMapping("/fiscal-responsibilities")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<List<FiscalResponsibilityResponse>> listFiscalResponsibilities() {
        return ResponseEntity.ok(catalogUseCase.listFiscalResponsibilities());
    }

    @GetMapping("/taxes")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<List<TaxResponse>> listTaxes() {
        return ResponseEntity.ok(catalogUseCase.listTaxes());
    }
}
