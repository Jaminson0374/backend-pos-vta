package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PriceListRequest;
import co.posinvent.application.dto.PriceListResponse;
import co.posinvent.application.port.in.PriceListPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/price-lists")
public class PriceListController {

    private final PriceListPort useCase;

    public PriceListController(PriceListPort useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<PriceListResponse> listAll() {
        return useCase.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PriceListResponse create(@Valid @RequestBody PriceListRequest request) {
        return useCase.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PriceListResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PriceListRequest request
    ) {
        return ResponseEntity.ok(useCase.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        useCase.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
