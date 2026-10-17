package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.AdvanceRequest;
import co.posinvent.application.dto.AdvanceResponse;
import co.posinvent.application.dto.ApplyAdvanceRequest;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.usecase.ApplyAdvanceUseCase;
import co.posinvent.application.usecase.PaymentUseCase;
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
@RequestMapping("/api/v1/payments/advances")
public class AdvanceController {

    private final PaymentUseCase paymentUseCase;
    private final ApplyAdvanceUseCase applyAdvanceUseCase;

    public AdvanceController(
            PaymentUseCase paymentUseCase,
            ApplyAdvanceUseCase applyAdvanceUseCase
    ) {
        this.paymentUseCase = paymentUseCase;
        this.applyAdvanceUseCase = applyAdvanceUseCase;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<AdvanceResponse> create(
            @Valid @RequestBody AdvanceRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentUseCase.createAdvance(request, principal.userId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<PageResponse<AdvanceResponse>> list(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(paymentUseCase.listAdvances(supplierId, pageable));
    }

    @PostMapping("/{id}/apply")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<AdvanceResponse> apply(
            @PathVariable UUID id,
            @Valid @RequestBody ApplyAdvanceRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        if (!id.equals(request.advancePaymentId())) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(applyAdvanceUseCase.apply(request, principal.userId()));
    }
}
