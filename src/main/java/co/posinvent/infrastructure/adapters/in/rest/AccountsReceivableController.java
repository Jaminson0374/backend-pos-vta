package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.AccountsReceivableResponse;
import co.posinvent.application.dto.ArAgingResponse;
import co.posinvent.application.dto.InterestCalculationResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.service.InterestCalculationService;
import co.posinvent.application.usecase.AccountsReceivableUseCase;
import co.posinvent.domain.model.AccountsReceivable;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts-receivable")
public class AccountsReceivableController {

    private final AccountsReceivableUseCase arUseCase;
    private final InterestCalculationService interestService;
    private final AccountsReceivableRepository arRepo;

    public AccountsReceivableController(
            AccountsReceivableUseCase arUseCase,
            InterestCalculationService interestService,
            AccountsReceivableRepository arRepo) {
        this.arUseCase = arUseCase;
        this.interestService = interestService;
        this.arRepo = arRepo;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<PageResponse<AccountsReceivableResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID clientId,
            @RequestParam(required = false) String status
    ) {
        var result = arUseCase.list(page, size, clientId, status);
        return ResponseEntity.ok(PageResponse.from(
                result,
                r -> r
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<AccountsReceivableResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(arUseCase.getById(id));
    }

    @PostMapping("/calculate-interest")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InterestCalculationResponse> calculateInterest() {
        return ResponseEntity.ok(interestService.calculateAllOverdueInterest());
    }

    @GetMapping("/intereses")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<Page<AccountsReceivableResponse>> getInterestSummary(
            @RequestParam(required = false) UUID clientId) {
        Page<AccountsReceivable> page;
        if (clientId != null) {
            page = arRepo.findByClientId(clientId, PageRequest.of(0, 100));
        } else {
            page = arRepo.findAll(PageRequest.of(0, 100));
        }
        var withInterest = page.stream()
                .filter(ar -> ar.interestAmount() != null
                        && ar.interestAmount().compareTo(BigDecimal.ZERO) > 0)
                .map(AccountsReceivableResponse::from)
                .toList();
        return ResponseEntity.ok(new PageImpl<>(withInterest, PageRequest.of(0, 100), withInterest.size()));
    }

    @GetMapping("/aging")
    @PreAuthorize("hasAnyRole('ADMIN','CAJERO','CONTADOR')")
    public ResponseEntity<ArAgingResponse> getAging(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf
    ) {
        return ResponseEntity.ok(arUseCase.getAging(asOf));
    }
}
