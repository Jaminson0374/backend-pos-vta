package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.AnimalRequest;
import co.posinvent.application.dto.AnimalResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.port.in.AnimalPort;
import co.posinvent.domain.model.Animal.AnimalStatus;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/animals")
public class AnimalController {

    private final AnimalPort animalUseCase;

    public AnimalController(AnimalPort animalUseCase) {
        this.animalUseCase = animalUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CARNICERO','AUXILIAR')")
    public ResponseEntity<PageResponse<AnimalResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AnimalStatus status,
            @RequestParam(required = false) String search
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receptionDate"));
        PageResponse<AnimalResponse> result;
        if (search != null && !search.isBlank()) {
            result = animalUseCase.searchByIcaLot(search, pageable);
        } else if (status != null) {
            result = animalUseCase.listByStatus(status, pageable);
        } else {
            result = animalUseCase.list(pageable);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CARNICERO','AUXILIAR')")
    public ResponseEntity<AnimalResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(animalUseCase.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CARNICERO')")
    public ResponseEntity<AnimalResponse> create(
            @Valid @RequestBody AnimalRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(animalUseCase.create(request, principal.userId()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CARNICERO')")
    public ResponseEntity<AnimalResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AnimalRequest request
    ) {
        return ResponseEntity.ok(animalUseCase.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        animalUseCase.delete(id);
        return ResponseEntity.noContent().build();
    }
}
