package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.WarehouseAccessResponse;
import co.posinvent.application.usecase.GetCurrentWarehouseAccessUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/warehouses")
@PreAuthorize("isAuthenticated()")
public class CurrentWarehouseAccessController {

    private final GetCurrentWarehouseAccessUseCase useCase;

    public CurrentWarehouseAccessController(GetCurrentWarehouseAccessUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public ResponseEntity<WarehouseAccessResponse> current() {
        return ResponseEntity.ok(useCase.execute());
    }
}
