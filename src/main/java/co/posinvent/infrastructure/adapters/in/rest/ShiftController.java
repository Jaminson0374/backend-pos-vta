package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.CashCountResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.RegisterCashCountRequest;
import co.posinvent.application.usecase.CashCountRequest;
import co.posinvent.application.usecase.CloseShiftUseCase;
import co.posinvent.application.usecase.CreateShiftUseCase;
import co.posinvent.application.usecase.RegisterCashCountUseCase;
import co.posinvent.application.usecase.ShiftRequest;
import co.posinvent.application.usecase.ShiftResponse;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.ShiftStatus;
import co.posinvent.domain.repository.ShiftRepository;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shifts")
public class ShiftController {

    private final CreateShiftUseCase createShiftUseCase;
    private final CloseShiftUseCase closeShiftUseCase;
    private final RegisterCashCountUseCase registerCashCountUseCase;
    private final ShiftRepository shiftRepository;

    public ShiftController(
            CreateShiftUseCase createShiftUseCase,
            CloseShiftUseCase closeShiftUseCase,
            RegisterCashCountUseCase registerCashCountUseCase,
            ShiftRepository shiftRepository
    ) {
        this.createShiftUseCase = createShiftUseCase;
        this.closeShiftUseCase = closeShiftUseCase;
        this.registerCashCountUseCase = registerCashCountUseCase;
        this.shiftRepository = shiftRepository;
    }

    @PostMapping("/open")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<ShiftResponse> open(
            @Valid @RequestBody ShiftRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(createShiftUseCase.execute(request, principal.userId()));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<ShiftResponse> close(
            @PathVariable UUID id,
            @Valid @RequestBody CashCountRequest cashCount,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.ok(closeShiftUseCase.execute(id, cashCount));
    }

    @PostMapping("/{id}/cash-counts")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<CashCountResponse> registerCashCount(
            @PathVariable UUID id,
            @Valid @RequestBody RegisterCashCountRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(registerCashCountUseCase.execute(id, request, principal.userId()));
    }

    @GetMapping("/{id}/cash-counts")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<List<CashCountResponse>> listCashCounts(@PathVariable UUID id) {
        return ResponseEntity.ok(registerCashCountUseCase.history(id));
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<ShiftResponse> getActive(@RequestParam UUID cashRegisterId) {
        var shift = shiftRepository.findByCashRegisterIdAndStatus(cashRegisterId, ShiftStatus.OPEN)
                .orElseThrow(() -> new ResourceNotFoundException("Turno activo para caja", cashRegisterId));
        return ResponseEntity.ok(ShiftResponse.from(shift));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<PageResponse<ShiftResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openingTime"));
        var result = shiftRepository.findAll(pageable);
        return ResponseEntity.ok(PageResponse.from(result, ShiftResponse::from));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<ShiftResponse> getById(@PathVariable UUID id) {
        var shift = shiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno", id));
        return ResponseEntity.ok(ShiftResponse.from(shift));
    }

    @GetMapping("/{id}/z-report")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<String> getZReport(@PathVariable UUID id) {
        var shift = shiftRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Turno", id));

        if (shift.status() != ShiftStatus.CLOSED) {
            throw new IllegalStateException("El turno no está cerrado. No hay reporte Z disponible.");
        }

        var zReport = shift.zReportUrl();
        if (zReport == null || zReport.isBlank()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(zReport);
    }
}
