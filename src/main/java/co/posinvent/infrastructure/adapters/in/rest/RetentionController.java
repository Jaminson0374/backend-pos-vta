package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.ErrorResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.domain.model.SupplierInvoice;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.SupplierInvoiceRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

@RestController
@RequestMapping("/api/v1/retenciones")
public class RetentionController {

    private final SupplierInvoiceRepository invoiceRepo;
    private final ThirdPartyRepository thirdPartyRepo;

    public RetentionController(SupplierInvoiceRepository invoiceRepo, ThirdPartyRepository thirdPartyRepo) {
        this.invoiceRepo = invoiceRepo;
        this.thirdPartyRepo = thirdPartyRepo;
    }

    @Transactional(readOnly = true)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AUXILIAR','CONTADOR')")
    public ResponseEntity<?> list(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        YearMonth month;
        try {
            month = period != null ? YearMonth.parse(period) : null;
        } catch (DateTimeParseException ex) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("INVALID_PERIOD",
                            "El período debe tener el formato YYYY-MM"));
        }

        var pageable = PageRequest.of(page, size);

        // Aggregate retentions by supplier from invoices
        var invoices = (supplierId != null)
                ? invoiceRepo.findBySupplierId(supplierId, Pageable.unpaged()).getContent()
                : invoiceRepo.findAll(Pageable.unpaged()).getContent();

        var grouped = new LinkedHashMap<UUID, RetentionEntry>();
        for (var inv : invoices) {
            if (month != null && !issuedIn(inv, month)) {
                continue;
            }
            var sid = inv.supplierId();
            var existing = grouped.get(sid);
            var invRet = inv.retentionTotal() != null ? inv.retentionTotal() : BigDecimal.ZERO;
            if (existing != null) {
                grouped.put(sid, new RetentionEntry(sid, existing.supplierName(),
                        existing.invoiceCount() + 1,
                        existing.retentionTotal().add(invRet)));
            } else {
                var name = thirdPartyRepo.findById(sid).map(ThirdParty::name).orElse("Desconocido");
                grouped.put(sid, new RetentionEntry(sid, name, 1, invRet));
            }
        }

        var entries = new ArrayList<>(grouped.values());
        entries.sort((a, b) -> b.retentionTotal().compareTo(a.retentionTotal()));

        // Manual pagination
        var start = page * size;
        var end = Math.min(start + size, entries.size());
        var content = start >= entries.size() ? List.<RetentionEntry>of() : entries.subList(start, end);

        return ResponseEntity.ok(new PageResponse<>(content, page, size, entries.size(),
                (int) Math.ceil((double) entries.size() / size), end >= entries.size()));
    }

    /** Whether the invoice issue date falls inside the requested month (both bounds inclusive). */
    private static boolean issuedIn(SupplierInvoice invoice, YearMonth month) {
        LocalDate issueDate = invoice.issueDate();
        return issueDate != null
                && !issueDate.isBefore(month.atDay(1))
                && !issueDate.isAfter(month.atEndOfMonth());
    }

    public record RetentionEntry(
            UUID supplierId,
            String supplierName,
            int invoiceCount,
            BigDecimal retentionTotal
    ) {}
}
