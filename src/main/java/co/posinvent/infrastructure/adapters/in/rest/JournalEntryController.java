package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.ManualJournalEntryRequest;
import co.posinvent.application.usecase.CreateJournalEntryUseCase;
import co.posinvent.domain.model.JournalEntryLine;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/journal-entries")
public class JournalEntryController {
    private final CreateJournalEntryUseCase useCase;
    public JournalEntryController(CreateJournalEntryUseCase u) { this.useCase = u; }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<?> createManual(@Valid @RequestBody ManualJournalEntryRequest request) {
        var lines = request.lines().stream()
                .map(l -> new JournalEntryLine(null, null, l.accountId(),
                        l.debit() != null ? l.debit() : BigDecimal.ZERO,
                        l.credit() != null ? l.credit() : BigDecimal.ZERO,
                        l.description()))
                .toList();
        return ResponseEntity.ok(
                useCase.createManual(request.entryDate(), request.description(), request.justification(), lines));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(useCase.list(sourceType, from, to, page, size));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<?> ledger(
            @RequestParam UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(useCase.ledger(accountId, from, to));
    }

    @GetMapping("/trial-balance")
    @PreAuthorize("hasAnyRole('ADMIN','CONTADOR')")
    public ResponseEntity<?> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(useCase.trialBalance(from, to));
    }
}
