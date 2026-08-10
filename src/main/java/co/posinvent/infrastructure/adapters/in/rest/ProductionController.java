package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.FormulaComponentResponse;
import co.posinvent.application.dto.ProduceRequest;
import co.posinvent.application.dto.ProduceResponse;
import co.posinvent.application.usecase.FormulaProductionUseCase;
import co.posinvent.application.usecase.ProductFormulaUseCase;
import co.posinvent.domain.model.ProductFormula;
import co.posinvent.infrastructure.adapters.out.security.PosUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ProductionController {

    private final FormulaProductionUseCase productionUseCase;
    private final ProductFormulaUseCase formulaUseCase;

    public ProductionController(FormulaProductionUseCase productionUseCase, ProductFormulaUseCase formulaUseCase) {
        this.productionUseCase = productionUseCase;
        this.formulaUseCase = formulaUseCase;
    }

    @PostMapping("/production/batches")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProduceResponse> produce(@Valid @RequestBody ProduceRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        UUID operatorId = (auth != null && auth.getPrincipal() instanceof PosUserDetails user)
                ? user.userId()
                : null;
        return ResponseEntity.status(HttpStatus.CREATED).body(productionUseCase.produce(request, operatorId));
    }

    @GetMapping("/production/batches")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<List<ProduceResponse>> listBatches(@RequestParam UUID formulaId) {
        // Simple list - just return placeholder
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/production/batches/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<ProduceResponse> getBatch(@PathVariable UUID id) {
        return ResponseEntity.ok(null); // placeholder
    }

    @GetMapping("/products/{productId}/formulas")
    public ResponseEntity<List<ProductFormula>> listFormulas(@PathVariable UUID productId) {
        return ResponseEntity.ok(formulaUseCase.list(productId));
    }

    @PostMapping("/products/{productId}/formulas")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductFormula> addFormula(
            @PathVariable UUID productId,
            @RequestBody AddFormulaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(formulaUseCase.add(productId, request.componentProductId(),
                        request.quantity(), request.unitOfMeasureId(), request.sequenceNumber(), request.notes()));
    }

    @PutMapping("/products/{productId}/formulas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductFormula> updateFormula(
            @PathVariable UUID id,
            @RequestBody UpdateFormulaRequest request) {
        return ResponseEntity.ok(formulaUseCase.update(id, request.quantity(),
                request.unitOfMeasureId(), request.sequenceNumber(), request.notes()));
    }

    @DeleteMapping("/products/{productId}/formulas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeFormula(@PathVariable UUID id) {
        formulaUseCase.remove(id);
        return ResponseEntity.noContent().build();
    }

    record AddFormulaRequest(UUID componentProductId, java.math.BigDecimal quantity,
                              UUID unitOfMeasureId, int sequenceNumber, String notes) {}

    record UpdateFormulaRequest(java.math.BigDecimal quantity, UUID unitOfMeasureId,
                                 int sequenceNumber, String notes) {}
}
