package co.posinvent.application.usecase;

import co.posinvent.application.dto.AccountsPayableResponse;
import co.posinvent.application.dto.ApAgingResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.AccountsPayable;
import co.posinvent.domain.model.DebitCreditNote;
import co.posinvent.domain.model.SupplierInvoice;
import co.posinvent.domain.repository.AccountsPayableRepository;
import co.posinvent.domain.repository.SupplierInvoiceRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AccountsPayableUseCase {

    private final AccountsPayableRepository apRepo;
    private final ThirdPartyRepository thirdPartyRepo;
    private final SupplierInvoiceRepository invoiceRepo;

    public AccountsPayableUseCase(
            AccountsPayableRepository apRepo,
            ThirdPartyRepository thirdPartyRepo,
            SupplierInvoiceRepository invoiceRepo
    ) {
        this.apRepo = apRepo;
        this.thirdPartyRepo = thirdPartyRepo;
        this.invoiceRepo = invoiceRepo;
    }

    // ── Create from Invoice ────────────────────────────────────────────────

    @Transactional
    public AccountsPayableResponse createFromInvoice(SupplierInvoice invoice) {
        // Compute due date using supplier creditDays
        var supplier = thirdPartyRepo.findById(invoice.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", invoice.supplierId()));
        var creditDays = supplier.creditDays() > 0 ? supplier.creditDays() : 30;
        var dueDate = invoice.dueDate() != null ? invoice.dueDate() : LocalDate.now().plusDays(creditDays);

        var ap = new AccountsPayable(
                null,
                invoice.supplierId(),
                invoice.id(),
                null,
                invoice.total(),
                BigDecimal.ZERO,
                invoice.total(),
                dueDate,
                AccountsPayable.ApStatus.OPEN,
                null,
                null
        );
        var saved = apRepo.save(ap);

        return enrich(AccountsPayableResponse.from(saved));
    }

    // ── Apply Debit/Credit Note ────────────────────────────────────────────

    @Transactional
    public AccountsPayableResponse applyDebitNote(DebitCreditNote note) {
        // Find the AP record for the supplier_invoice referenced by the note
        var ap = apRepo.findByDocumentId(note.supplierInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("CxP para factura", note.supplierInvoiceId()));

        var newTotal = ap.totalAmount();
        if (note.isDebitNote()) {
            // DEBIT_NOTE increases what we owe
            newTotal = newTotal.add(note.amount());
        } else if (note.isCreditNote()) {
            // CREDIT_NOTE decreases what we owe
            newTotal = newTotal.subtract(note.amount());
            if (newTotal.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("AP_NEGATIVE_TOTAL",
                        "La nota crédito (" + note.amount()
                        + ") excede el saldo total de la CxP (" + ap.totalAmount() + ")");
            }
        }

        var newOutstanding = AccountsPayable.computeOutstanding(newTotal, ap.paidAmount());
        var newStatus = AccountsPayable.computeStatus(newTotal, ap.paidAmount());

        var updated = new AccountsPayable(
                ap.id(), ap.supplierId(), ap.documentId(),
                note.id(),
                newTotal, ap.paidAmount(), newOutstanding,
                ap.dueDate(), newStatus,
                ap.createdAt(), null
        );
        var saved = apRepo.save(updated);
        return enrich(AccountsPayableResponse.from(saved));
    }

    // ── Mark Overdue ───────────────────────────────────────────────────────

    @Transactional
    public int markOverdue() {
        var today = LocalDate.now();
        var overdue = apRepo.findOverdueBefore(today);
        int count = 0;
        for (var ap : overdue) {
            if (ap.status() == AccountsPayable.ApStatus.OPEN
                    || ap.status() == AccountsPayable.ApStatus.PARTIAL) {
                var updated = new AccountsPayable(
                        ap.id(), ap.supplierId(), ap.documentId(),
                        ap.debitCreditNoteId(),
                        ap.totalAmount(), ap.paidAmount(), ap.outstanding(),
                        ap.dueDate(), AccountsPayable.ApStatus.OVERDUE,
                        ap.createdAt(), null
                );
                apRepo.save(updated);
                count++;
            }
        }
        return count;
    }

    // ── Queries ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AccountsPayableResponse> list(int page, int size,
                                               UUID supplierId, String status) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dueDate"));

        if (supplierId != null && status != null && !status.isBlank()) {
            var apStatus = AccountsPayable.ApStatus.valueOf(status.toUpperCase());
            return apRepo.findBySupplierIdAndStatus(supplierId, apStatus, pageable)
                    .map(AccountsPayableResponse::from)
                    .map(this::enrich);
        }
        if (supplierId != null) {
            return apRepo.findBySupplierId(supplierId, pageable)
                    .map(AccountsPayableResponse::from)
                    .map(this::enrich);
        }
        if (status != null && !status.isBlank()) {
            var apStatus = AccountsPayable.ApStatus.valueOf(status.toUpperCase());
            return apRepo.findByStatus(apStatus, pageable)
                    .map(AccountsPayableResponse::from)
                    .map(this::enrich);
        }
        return apRepo.findAll(pageable)
                .map(AccountsPayableResponse::from)
                .map(this::enrich);
    }

    @Transactional(readOnly = true)
    public AccountsPayableResponse getById(UUID id) {
        var ap = apRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CxP", id));
        return enrich(AccountsPayableResponse.from(ap));
    }

    @Transactional(readOnly = true)
    public ApAgingResponse getAging(LocalDate asOf) {
        var reference = asOf != null ? asOf : LocalDate.now();

        var all = apRepo.findAll(Pageable.unpaged()).getContent();

        int cCurrent = 0, c1to30 = 0, c31to60 = 0, c61to90 = 0, c91plus = 0;
        BigDecimal tCurrent = BigDecimal.ZERO;
        BigDecimal t1to30 = BigDecimal.ZERO;
        BigDecimal t31to60 = BigDecimal.ZERO;
        BigDecimal t61to90 = BigDecimal.ZERO;
        BigDecimal t91plus = BigDecimal.ZERO;

        for (var ap : all) {
            if (ap.status() == AccountsPayable.ApStatus.PAID) continue;

            long days = ChronoUnit.DAYS.between(ap.dueDate(), reference);

            if (days <= 0) {
                cCurrent++;
                tCurrent = tCurrent.add(ap.outstanding());
            } else if (days <= 30) {
                c1to30++;
                t1to30 = t1to30.add(ap.outstanding());
            } else if (days <= 60) {
                c31to60++;
                t31to60 = t31to60.add(ap.outstanding());
            } else if (days <= 90) {
                c61to90++;
                t61to90 = t61to90.add(ap.outstanding());
            } else {
                c91plus++;
                t91plus = t91plus.add(ap.outstanding());
            }
        }

        var totalOutstanding = tCurrent.add(t1to30).add(t31to60).add(t61to90).add(t91plus);

        return new ApAgingResponse(
                ApAgingResponse.AgingBucket.of(cCurrent, tCurrent),
                ApAgingResponse.AgingBucket.of(c1to30, t1to30),
                ApAgingResponse.AgingBucket.of(c31to60, t31to60),
                ApAgingResponse.AgingBucket.of(c61to90, t61to90),
                ApAgingResponse.AgingBucket.of(c91plus, t91plus),
                totalOutstanding
        );
    }

    // ── Enrich ─────────────────────────────────────────────────────────────

    private AccountsPayableResponse enrich(AccountsPayableResponse r) {
        String supplierName = null;
        String docNumber = null;

        if (r.supplierId() != null) {
            supplierName = thirdPartyRepo.findById(r.supplierId())
                    .map(tp -> tp.name())
                    .orElse(null);
        }
        if (r.documentId() != null) {
            docNumber = invoiceRepo.findById(r.documentId())
                    .map(SupplierInvoice::invoiceNumber)
                    .orElse(null);
        }

        if (supplierName != null || docNumber != null) {
            return new AccountsPayableResponse(
                    r.id(), r.supplierId(),
                    supplierName != null ? supplierName : r.supplierName(),
                    r.documentId(),
                    docNumber != null ? docNumber : r.documentNumber(),
                    r.debitCreditNoteId(),
                    r.totalAmount(), r.paidAmount(), r.outstanding(),
                    r.dueDate(), r.status(),
                    r.createdAt(), r.updatedAt()
            );
        }
        return r;
    }
}
