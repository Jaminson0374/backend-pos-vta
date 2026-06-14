package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.PurchaseReturnRequest;
import co.posinvent.application.dto.PurchaseReturnResponse;
import co.posinvent.application.usecase.ProcessPurchaseReturnUseCase;
import co.posinvent.domain.repository.PurchaseReturnRepository;
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
@RequestMapping("/api/v1/purchase-returns")
public class PurchaseReturnController {

    private final ProcessPurchaseReturnUseCase processPurchaseReturnUseCase;
    private final PurchaseReturnRepository purchaseReturnRepository;

    public PurchaseReturnController(
            ProcessPurchaseReturnUseCase processPurchaseReturnUseCase,
            PurchaseReturnRepository purchaseReturnRepository
    ) {
        this.processPurchaseReturnUseCase = processPurchaseReturnUseCase;
        this.purchaseReturnRepository = purchaseReturnRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR')")
    public ResponseEntity<PurchaseReturnResponse> create(
            @Valid @RequestBody PurchaseReturnRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(processPurchaseReturnUseCase.execute(request, principal.userId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<PageResponse<PurchaseReturnResponse>> list(
            @RequestParam(required = false) UUID receiptId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "returnDate"));

        if (receiptId != null) {
            var existing = purchaseReturnRepository.findByReceiptId(receiptId);
            if (existing.isPresent()) {
                var pr = existing.get();
                var total = pr.lines().stream()
                        .map(l -> l.returnQty().multiply(l.unitCost()))
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                var response = PurchaseReturnResponse.from(pr, pr.lines().size(), total);
                var pageResp = new PageResponse<>(
                        java.util.List.of(response), 0, 1, 1, 1, true);
                return ResponseEntity.ok(pageResp);
            }
            return ResponseEntity.ok(new PageResponse<>(java.util.List.of(), 0, size, 0, 0, true));
        }

        var result = purchaseReturnRepository.findAll(pageable);
        return ResponseEntity.ok(PageResponse.from(
                result,
                pr -> {
                    var total = pr.lines().stream()
                            .map(l -> l.returnQty().multiply(l.unitCost()))
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    return PurchaseReturnResponse.from(pr, pr.lines().size(), total);
                }
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<PurchaseReturnResponse> getById(@PathVariable UUID id) {
        var pr = purchaseReturnRepository.findById(id)
                .orElseThrow(() -> new co.posinvent.domain.exception.ResourceNotFoundException(
                        "Devolución por remisión", id));

        var total = pr.lines().stream()
                .map(l -> l.returnQty().multiply(l.unitCost()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        return ResponseEntity.ok(PurchaseReturnResponse.from(pr, pr.lines().size(), total));
    }
}
