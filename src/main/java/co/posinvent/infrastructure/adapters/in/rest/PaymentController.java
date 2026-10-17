package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.PaymentRequest;
import co.posinvent.application.dto.PaymentResponse;
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
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentUseCase paymentUseCase;

    public PaymentController(PaymentUseCase paymentUseCase) {
        this.paymentUseCase = paymentUseCase;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<PageResponse<PaymentResponse>> list(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "paymentDate"));
        PageResponse<PaymentResponse> result;

        if (supplierId != null) {
            result = paymentUseCase.findBySupplier(supplierId, pageable);
        } else {
            result = paymentUseCase.list(pageable);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<PaymentResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(paymentUseCase.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody PaymentRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentUseCase.create(request, principal.userId()));
    }
}
