package co.posinvent.application.usecase;

import co.posinvent.application.dto.AccountsPayableResponse;
import co.posinvent.application.dto.AdvanceRequest;
import co.posinvent.application.dto.AdvanceResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.PaymentRequest;
import co.posinvent.application.dto.PaymentResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.AccountsPayable;
import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.Payment;
import co.posinvent.domain.model.Payment.InvoicePayment;
import co.posinvent.domain.model.SupplierAdvanceCreatedEvent;
import co.posinvent.domain.model.SupplierInvoice;
import co.posinvent.domain.model.SupplierPaymentEvent;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.PaymentRepository;
import co.posinvent.domain.repository.SupplierInvoiceRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.domain.service.PaymentDomainService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final AccountsPayableUseCase accountsPayableUseCase;
    private final PaymentDomainService domainService;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentUseCase(
            PaymentRepository paymentRepository,
            SupplierInvoiceRepository invoiceRepository,
            ThirdPartyRepository thirdPartyRepository,
            AccountsPayableUseCase accountsPayableUseCase,
            ApplicationEventPublisher eventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.thirdPartyRepository = thirdPartyRepository;
        this.accountsPayableUseCase = accountsPayableUseCase;
        this.domainService = new PaymentDomainService();
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(Pageable pageable) {
        return PageResponse.from(
                paymentRepository.findAll(pageable),
                this::toResponse
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> findBySupplier(UUID supplierId, Pageable pageable) {
        return PageResponse.from(
                paymentRepository.findBySupplierId(supplierId, pageable),
                this::toResponse
        );
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(UUID id) {
        return paymentRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id));
    }

    /** Maps a payment to its response, resolving the supplier display name (M7). */
    private PaymentResponse toResponse(Payment payment) {
        var supplierName = payment.supplierId() == null
                ? null
                : thirdPartyRepository.findById(payment.supplierId())
                        .map(ThirdParty::name)
                        .orElse(null);
        return PaymentResponse.from(payment, supplierName);
    }

    @Transactional
    public PaymentResponse create(PaymentRequest request, UUID operatorId) {
        // 1. Validate payment amount > 0
        domainService.validateAmount(request.amount());

        // 2. Group the applied amount per invoice. A duplicated invoice line is rejected with a
        // business error instead of crashing Collectors.toMap or the invoice_payments PK.
        var appliedByInvoice = new LinkedHashMap<UUID, BigDecimal>();
        for (var ip : request.invoicePayments()) {
            if (appliedByInvoice.putIfAbsent(ip.invoiceId(), ip.appliedAmount()) != null) {
                throw new BusinessException("DUPLICATE_INVOICE_PAYMENT",
                        "La factura " + ip.invoiceId() + " aparece más de una vez en el pago");
            }
        }

        var invoices = appliedByInvoice.keySet().stream()
                .map(id -> invoiceRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Factura de proveedor", id)))
                .toList();

        var domainInvoicePayments = appliedByInvoice.entrySet().stream()
                .map(e -> new InvoicePayment(e.getKey(), e.getValue()))
                .toList();

        // 3. Validate invoice payability against the remaining balance (A1). The CxP outstanding is
        // the source of truth; when a reconciled invoice has no CxP yet we fall back to its total.
        var apByInvoice = new LinkedHashMap<UUID, AccountsPayableResponse>();
        var pendingByInvoice = new LinkedHashMap<UUID, BigDecimal>();
        for (var invoice : invoices) {
            var ap = accountsPayableUseCase.findByDocumentId(invoice.id()).orElse(null);
            if (ap != null) {
                apByInvoice.put(invoice.id(), ap);
                pendingByInvoice.put(invoice.id(), ap.outstanding());
            } else {
                pendingByInvoice.put(invoice.id(), invoice.total());
            }
        }

        domainService.validateInvoicePayments(invoices, domainInvoicePayments, request.amount(),
                pendingByInvoice);

        // 4. Create Payment record
        var payment = new Payment(
                null,
                request.supplierId(),
                request.amount(),
                request.paymentDate(),
                request.method(),
                request.reference(),
                request.notes(),
                operatorId,
                null,
                null,
                null,
                domainInvoicePayments
        );

        var savedPayment = paymentRepository.save(payment);

        // C4: publish the payment so the accounting listener posts the payable/cash entry after
        // commit. The payment carries no sequential number, so its reference is used when present,
        // else its id.
        eventPublisher.publishEvent(new SupplierPaymentEvent(
                this, savedPayment.id(), paymentNumber(savedPayment),
                savedPayment.amount(), savedPayment.paymentDate()));

        // 5. Apply each invoice payment to its CxP (single source of truth for the payable side,
        // hallazgo C2) and mirror the resulting status on the invoice. Invoices are processed in a
        // deterministic order so multi-invoice payments never deadlock on row locks.
        var orderedInvoices = invoices.stream()
                .sorted(Comparator.comparing(SupplierInvoice::id))
                .toList();

        for (var invoice : orderedInvoices) {
            var appliedAmount = appliedByInvoice.get(invoice.id());

            InvoiceStatus newStatus;
            if (apByInvoice.containsKey(invoice.id())) {
                var updatedAp = accountsPayableUseCase.applyPayment(invoice.id(), appliedAmount);
                newStatus = AccountsPayable.ApStatus.valueOf(updatedAp.status()) == AccountsPayable.ApStatus.PAID
                        ? InvoiceStatus.PAID
                        : InvoiceStatus.RECONCILED;
            } else {
                // No CxP to update (legacy/standalone invoice): keep the previous invoice-level rule.
                newStatus = appliedAmount.compareTo(invoice.total()) >= 0
                        ? InvoiceStatus.PAID
                        : InvoiceStatus.RECONCILED;
            }

            invoiceRepository.save(withStatus(invoice, newStatus));
        }

        return toResponse(savedPayment);
    }

    /** Returns a copy of the invoice with a new status (used to keep the invoice coherent with the CxP). */
    private static SupplierInvoice withStatus(SupplierInvoice invoice, InvoiceStatus status) {
        return new SupplierInvoice(
                invoice.id(),
                invoice.supplierId(),
                invoice.invoiceNumber(),
                invoice.issueDate(),
                invoice.dueDate(),
                invoice.subtotal(),
                invoice.ivaTotal(),
                invoice.retentionTotal(),
                invoice.total(),
                status,
                invoice.notes(),
                invoice.createdBy(),
                invoice.createdAt(),
                null,
                invoice.version(),
                invoice.ocIds()
        );
    }

    /** Human-readable identifier for the journal description: the user reference, else the payment id. */
    private String paymentNumber(Payment payment) {
        return payment.reference() != null && !payment.reference().isBlank()
                ? payment.reference()
                : payment.id().toString();
    }

    /**
     * Registers an advance (anticipo) payment to a supplier.
     * Creates a payment with isAdvance=true, remainingAdvance=amount, no invoice payments.
     */
    @Transactional
    public AdvanceResponse createAdvance(AdvanceRequest request, UUID operatorId) {
        // 1. Validate payment amount > 0
        domainService.validateAmount(request.amount());

        // 2. Create Payment record with isAdvance flag
        var payment = new Payment(
                null,
                request.supplierId(),
                request.amount(),
                request.paymentDate(),
                request.method(),
                request.reference(),
                request.notes(),
                operatorId,
                null,
                null,
                null,
                true,
                request.amount(),
                List.of()
        );

        var savedPayment = paymentRepository.save(payment);

        // S3b: an unapplied advance is cash out that becomes an asset (debit 133005 / credit 1105).
        // Publish after the save so the listener posts the entry once the transaction commits.
        eventPublisher.publishEvent(new SupplierAdvanceCreatedEvent(
                this, savedPayment.id(), paymentNumber(savedPayment),
                savedPayment.amount(), savedPayment.paymentDate()));

        // The supplier balance is derived from the CxP (SUM(outstanding)). The unapplied advance
        // is an asset (PUC 133005), not a payable; applying it crosses the asset against the AP.
        // It never mutates the AR-only third_parties.current_balance.
        return AdvanceResponse.from(savedPayment);
    }

    /**
     * Lists all advance payments, optionally filtered by supplier.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdvanceResponse> listAdvances(UUID supplierId, Pageable pageable) {
        if (supplierId != null) {
            return PageResponse.from(
                    paymentRepository.findByIsAdvanceTrueAndSupplierId(supplierId, pageable),
                    AdvanceResponse::from
            );
        }
        return PageResponse.from(
                paymentRepository.findByIsAdvanceTrue(pageable),
                AdvanceResponse::from
        );
    }

    }
