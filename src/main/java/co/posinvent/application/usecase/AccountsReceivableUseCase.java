package co.posinvent.application.usecase;

import co.posinvent.application.dto.AccountsReceivableResponse;
import co.posinvent.application.dto.ArAgingResponse;
import co.posinvent.application.service.InterestCalculationService;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.AccountsReceivable;
import co.posinvent.domain.model.SalesDocument;
import co.posinvent.domain.repository.AccountsReceivableRepository;
import co.posinvent.domain.repository.SalesDocumentRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AccountsReceivableUseCase {

    private final AccountsReceivableRepository arRepo;
    private final ThirdPartyRepository thirdPartyRepo;
    private final SalesDocumentRepository documentRepo;
    private final InterestCalculationService interestCalculationService;

    public AccountsReceivableUseCase(
            AccountsReceivableRepository arRepo,
            ThirdPartyRepository thirdPartyRepo,
            SalesDocumentRepository documentRepo,
            InterestCalculationService interestCalculationService
    ) {
        this.arRepo = arRepo;
        this.thirdPartyRepo = thirdPartyRepo;
        this.documentRepo = documentRepo;
        this.interestCalculationService = interestCalculationService;
    }

    // ── Create from Invoice ────────────────────────────────────────────────

    @Transactional
    public AccountsReceivableResponse createFromInvoice(SalesDocument document) {
        if (!Boolean.TRUE.equals(document.isCreditSale())) {
            throw new BusinessException("AR_NOT_CREDIT",
                    "El documento no es una venta a crédito");
        }

        // Compute due date: now + client.creditDays
        var client = thirdPartyRepo.findById(document.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", document.clientId()));
        var creditDays = client.creditDays() > 0 ? client.creditDays() : 30;
        var dueDate = LocalDate.now().plusDays(creditDays);

        var ar = new AccountsReceivable(
                null,
                document.clientId(),
                document.id(),
                document.totalAmount(),
                BigDecimal.ZERO,
                document.totalAmount(),
                dueDate,
                AccountsReceivable.ArStatus.OPEN,
                null,
                null,
                null,
                BigDecimal.ZERO,
                null
        );
        var saved = arRepo.save(ar);

        return enrich(AccountsReceivableResponse.from(saved));
    }

    // ── Apply Payment ──────────────────────────────────────────────────────

    @Transactional
    public AccountsReceivableResponse applyPayment(UUID arId, BigDecimal amount) {
        var ar = arRepo.findById(arId)
                .orElseThrow(() -> new ResourceNotFoundException("CxC", arId));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("AR_INVALID_AMOUNT",
                    "El monto del pago debe ser mayor a cero");
        }

        var newPaid = ar.paidAmount().add(amount);
        if (newPaid.compareTo(ar.totalAmount()) > 0) {
            throw new BusinessException("AR_OVERPAYMENT",
                    "El monto aplicado (" + amount + ") excede el saldo pendiente ("
                    + ar.outstanding() + ")");
        }

        var newOutstanding = AccountsReceivable.computeOutstanding(ar.totalAmount(), newPaid);
        var newStatus = AccountsReceivable.computeStatus(ar.totalAmount(), newPaid);

        var updated = new AccountsReceivable(
                ar.id(), ar.clientId(), ar.documentId(),
                ar.totalAmount(), newPaid, newOutstanding,
                ar.dueDate(), newStatus,
                ar.createdAt(), null,
                ar.interestRate(), ar.interestAmount(), ar.lastInterestCalcDate()
        );
        var saved = arRepo.save(updated);
        return enrich(AccountsReceivableResponse.from(saved));
    }

    // ── Mark Overdue ───────────────────────────────────────────────────────

    @Transactional
    public int markOverdue() {
        var today = LocalDate.now();
        var overdue = arRepo.findOverdueBefore(today);
        int count = 0;
        for (var ar : overdue) {
            if (ar.status() == AccountsReceivable.ArStatus.OPEN
                    || ar.status() == AccountsReceivable.ArStatus.PARTIAL) {
                var updated = new AccountsReceivable(
                        ar.id(), ar.clientId(), ar.documentId(),
                        ar.totalAmount(), ar.paidAmount(), ar.outstanding(),
                        ar.dueDate(), AccountsReceivable.ArStatus.OVERDUE,
                        ar.createdAt(), null,
                        ar.interestRate(), ar.interestAmount(), ar.lastInterestCalcDate()
                );
                arRepo.save(updated);
                count++;
            }
        }

        // After marking ARs as overdue, calculate interest
        if (count > 0) {
            interestCalculationService.calculateOverdueInterest();
        }

        return count;
    }

    // ── Queries ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AccountsReceivableResponse> listByClient(UUID clientId, Pageable pageable) {
        return arRepo.findByClientId(clientId, pageable)
                .map(AccountsReceivableResponse::from)
                .map(this::enrich);
    }

    @Transactional(readOnly = true)
    public Page<AccountsReceivableResponse> listByStatus(AccountsReceivable.ArStatus status,
                                                          Pageable pageable) {
        return arRepo.findByStatus(status, pageable)
                .map(AccountsReceivableResponse::from)
                .map(this::enrich);
    }

    @Transactional(readOnly = true)
    public Page<AccountsReceivableResponse> list(int page, int size,
                                                  UUID clientId, String status) {
        var sort = Sort.by(Sort.Direction.DESC, "dueDate");
        var pageable = PageRequest.of(page, size, sort);

        if (clientId != null && status != null && !status.isBlank()) {
            var arStatus = AccountsReceivable.ArStatus.valueOf(status.toUpperCase());
            return arRepo.findByClientIdAndStatus(clientId, arStatus, pageable)
                    .map(AccountsReceivableResponse::from)
                    .map(this::enrich);
        }
        if (clientId != null) {
            return arRepo.findByClientId(clientId, pageable)
                    .map(AccountsReceivableResponse::from)
                    .map(this::enrich);
        }
        if (status != null && !status.isBlank()) {
            var arStatus = AccountsReceivable.ArStatus.valueOf(status.toUpperCase());
            return arRepo.findByStatus(arStatus, pageable)
                    .map(AccountsReceivableResponse::from)
                    .map(this::enrich);
        }
        return arRepo.findAll(pageable)
                .map(AccountsReceivableResponse::from)
                .map(this::enrich);
    }

    @Transactional(readOnly = true)
    public AccountsReceivableResponse getById(UUID id) {
        var ar = arRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CxC", id));
        return enrich(AccountsReceivableResponse.from(ar));
    }

    @Transactional(readOnly = true)
    public ArAgingResponse getAging(LocalDate asOf) {
        var reference = asOf != null ? asOf : LocalDate.now();

        var all = arRepo.findAll(Pageable.unpaged()).getContent();

        int cCurrent = 0, c1to30 = 0, c31to60 = 0, c61to90 = 0, c91plus = 0;
        BigDecimal tCurrent = BigDecimal.ZERO;
        BigDecimal t1to30 = BigDecimal.ZERO;
        BigDecimal t31to60 = BigDecimal.ZERO;
        BigDecimal t61to90 = BigDecimal.ZERO;
        BigDecimal t91plus = BigDecimal.ZERO;

        for (var ar : all) {
            if (ar.status() == AccountsReceivable.ArStatus.PAID) continue;

            long days = ChronoUnit.DAYS.between(ar.dueDate(), reference);
            // Negative days = due date is in the future → "current"
            // 0 days = due today → "current"
            // Positive days = overdue

            if (days <= 0) {
                cCurrent++;
                tCurrent = tCurrent.add(ar.outstanding());
            } else if (days <= 30) {
                c1to30++;
                t1to30 = t1to30.add(ar.outstanding());
            } else if (days <= 60) {
                c31to60++;
                t31to60 = t31to60.add(ar.outstanding());
            } else if (days <= 90) {
                c61to90++;
                t61to90 = t61to90.add(ar.outstanding());
            } else {
                c91plus++;
                t91plus = t91plus.add(ar.outstanding());
            }
        }

        var totalOutstanding = tCurrent.add(t1to30).add(t31to60).add(t61to90).add(t91plus);

        return new ArAgingResponse(
                ArAgingResponse.AgingBucket.of(cCurrent, tCurrent),
                ArAgingResponse.AgingBucket.of(c1to30, t1to30),
                ArAgingResponse.AgingBucket.of(c31to60, t31to60),
                ArAgingResponse.AgingBucket.of(c61to90, t61to90),
                ArAgingResponse.AgingBucket.of(c91plus, t91plus),
                totalOutstanding
        );
    }

    // ── Enrich ─────────────────────────────────────────────────────────────

    private AccountsReceivableResponse enrich(AccountsReceivableResponse r) {
        String clientName = null;
        String docNumber = null;

        if (r.clientId() != null) {
            clientName = thirdPartyRepo.findById(r.clientId())
                    .map(tp -> tp.name())
                    .orElse(null);
        }
        if (r.documentId() != null) {
            docNumber = documentRepo.findById(r.documentId())
                    .map(SalesDocument::documentNumber)
                    .orElse(null);
        }

        // Only create a new record if enrichment actually changed something
        if (clientName != null || docNumber != null) {
            return new AccountsReceivableResponse(
                    r.id(), r.clientId(),
                    clientName != null ? clientName : r.clientName(),
                    r.documentId(),
                    docNumber != null ? docNumber : r.documentNumber(),
                    r.totalAmount(), r.paidAmount(), r.outstanding(),
                    r.dueDate(), r.status(),
                    r.createdAt(), r.updatedAt(),
                    r.interestRate(), r.interestAmount(), r.lastInterestCalcDate()
            );
        }
        return r;
    }
}
