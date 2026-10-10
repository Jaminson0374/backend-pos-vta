package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.DesposteResponse;
import co.posinvent.application.dto.ManualDesposteRequest;
import co.posinvent.application.dto.ManualDesposteResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.port.in.DesposteQueryPort;
import co.posinvent.application.usecase.ManualDesposteUseCase;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/despostes")
public class DesposteController {

    private final ManualDesposteUseCase manualDesposteUseCase;
    private final DesposteQueryPort desposteQueryPort;

    public DesposteController(ManualDesposteUseCase manualDesposteUseCase,
                              DesposteQueryPort desposteQueryPort) {
        this.manualDesposteUseCase = manualDesposteUseCase;
        this.desposteQueryPort = desposteQueryPort;
    }

    @PostMapping("/manual")
    @PreAuthorize("hasAnyRole('ADMIN','CARNICERO')")
    public ResponseEntity<ManualDesposteResponse> processManual(@Valid @RequestBody ManualDesposteRequest request) {
        return ResponseEntity.ok(manualDesposteUseCase.processManual(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CARNICERO')")
    public ResponseEntity<PageResponse<DesposteResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(desposteQueryPort.findPage(from, to, page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CARNICERO')")
    public ResponseEntity<DesposteResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(desposteQueryPort.findById(id));
    }
}
