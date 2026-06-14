package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.DevolutionRequest;
import co.posinvent.application.dto.DevolutionResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.ProductResponse;
import co.posinvent.application.usecase.CheckoutRequest;
import co.posinvent.application.usecase.CheckoutResponse;
import co.posinvent.application.usecase.PosCheckoutUseCase;
import co.posinvent.application.usecase.PosDevolutionUseCase;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pos")
public class PosController {

    private final PosCheckoutUseCase checkoutUseCase;
    private final PosDevolutionUseCase devolutionUseCase;
    private final ProductRepository productRepo;
    private final SalesDocumentRepository salesDocumentRepo;

    public PosController(PosCheckoutUseCase checkoutUseCase,
                         PosDevolutionUseCase devolutionUseCase,
                         ProductRepository productRepo,
                         SalesDocumentRepository salesDocumentRepo) {
        this.checkoutUseCase = checkoutUseCase;
        this.devolutionUseCase = devolutionUseCase;
        this.productRepo = productRepo;
        this.salesDocumentRepo = salesDocumentRepo;
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<CheckoutResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(checkoutUseCase.checkout(request, principal.userId()));
    }

    @PostMapping("/devolutions")
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
    public ResponseEntity<DevolutionResponse> processDevolution(
            @Valid @RequestBody DevolutionRequest request,
            @AuthenticationPrincipal PosUserDetails principal
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(devolutionUseCase.processDevolution(request, principal.userId()));
    }

    @GetMapping("/devolutions")
    @PreAuthorize("hasAnyRole('ADMIN','VENDEDOR')")
    public ResponseEntity<List<SalesDocument>> getDevolutions(@RequestParam UUID invoiceId) {
        return ResponseEntity.ok(salesDocumentRepo.findBySourceDocumentId(invoiceId));
    }

    @GetMapping("/products/search")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO')")
    public ResponseEntity<PageResponse<ProductResponse>> searchProducts(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var pageable = PageRequest.of(page, size);
        var result = productRepo.searchByName(q, pageable);
        return ResponseEntity.ok(PageResponse.from(result, ProductResponse::from));
    }
}
