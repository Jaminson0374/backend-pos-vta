package co.posinvent.application.usecase;

import co.posinvent.application.dto.ApplyAdvanceRequest;
import co.posinvent.application.dto.AdvanceResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.AccountsPayable;
import co.posinvent.domain.model.AdvanceApplication;
import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.Payment;
import co.posinvent.domain.model.SupplierAdvanceAppliedEvent;
import co.posinvent.domain.model.SupplierInvoice;
import co.posinvent.domain.repository.AdvanceApplicationRepository;
import co.posinvent.domain.repository.PaymentRepository;
import co.posinvent.domain.repository.SupplierInvoiceRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ApplyAdvanceUseCase {

    private final PaymentRepository paymentRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final AdvanceApplicationRepository advanceApplicationRepository;
    private final AccountsPayableUseCase accountsPayableUseCase;
    private final ApplicationEventPublisher eventPublisher;

    public ApplyAdvanceUseCase(
            PaymentRepository paymentRepository,
            SupplierInvoiceRepository invoiceRepository,
            AdvanceApplicationRepository advanceApplicationRepository,
            AccountsPayableUseCase accountsPayableUseCase,
            ApplicationEventPublisher eventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.advanceApplicationRepository = advanceApplicationRepository;
        this.accountsPayableUseCase = accountsPayableUseCase;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AdvanceResponse apply(ApplyAdvanceRequest request, UUID operatorId) {
        // 1. Load advance payment
        var advance = paymentRepository.findById(request.advancePaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Anticipo", request.advancePaymentId()));

        if (!advance.isAdvance()) {
            throw new BusinessException("NOT_AN_ADVANCE", "El pago no es un anticipo.");
        }

        var remaining = advance.remainingAdvance();
        if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("ADVANCE_EXHAUSTED", "El anticipo ya no tiene saldo disponible.");
        }

        if (request.appliedAmount().compareTo(remaining) > 0) {
            throw new BusinessException(
                    "INSUFFICIENT_ADVANCE",
                    "El monto a aplicar (" + request.appliedAmount()
                    + ") excede el saldo disponible del anticipo (" + remaining + ")."
            );
        }

        // 2. Load invoice
        var invoice = invoiceRepository.findById(request.invoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Factura", request.invoiceId()));

        if (invoice.status() != InvoiceStatus.PENDING && invoice.status() != InvoiceStatus.RECONCILED) {
            throw new BusinessException(
                    "INVOICE_NOT_APPLICABLE",
                    "La factura " + invoice.invoiceNumber()
                    + " no está en estado PENDING ni RECONCILED. Estado: " + invoice.status()
            );
        }

        // 3. Validate supplier match
        if (!invoice.supplierId().equals(advance.supplierId())) {
            throw new BusinessException(
                    "SUPPLIER_MISMATCH",
                    "La factura pertenece a otro proveedor. Anticipo: " + advance.supplierId()
                    + ", Factura: " + invoice.supplierId()
            );
        }

        // 4. Create AdvanceApplication record
        var application = new AdvanceApplication(
                null,
                request.advancePaymentId(),
                request.invoiceId(),
                request.appliedAmount(),
                LocalDate.now(),
                operatorId,
                null
        );
        var savedApplication = advanceApplicationRepository.save(application);
        if (savedApplication.id() == null) {
            // Defensive: the adapter normally returns the generated id; re-load when it does not.
            savedApplication = reloadApplication(application);
        }

        // 5. Decrement remainingAdvance
        var newRemaining = remaining.subtract(request.appliedAmount());

        var updatedAdvance = new Payment(
                advance.id(),
                advance.supplierId(),
                advance.amount(),
                advance.paymentDate(),
                advance.method(),
                advance.reference(),
                advance.notes(),
                advance.createdBy(),
                advance.createdAt(),
                advance.updatedAt(),
                advance.version(),
                true,
                newRemaining,
                advance.invoicePayments()
        );
        paymentRepository.save(updatedAdvance);

        // 6. Keep the CxP consistent (M3): an applied advance is a real payment against the
        //    invoice's payable. Ensure the payable exists, apply the amount to it and mirror the
        //    resulting status back on the invoice (PAID when fully settled, RECONCILED otherwise).
        if (accountsPayableUseCase.findByDocumentId(invoice.id()).isEmpty()) {
            accountsPayableUseCase.createFromInvoice(invoice);
        }
        var updatedAp = accountsPayableUseCase.applyPayment(invoice.id(), request.appliedAmount());
        var newStatus = AccountsPayable.ApStatus.valueOf(updatedAp.status()) == AccountsPayable.ApStatus.PAID
                ? InvoiceStatus.PAID
                : InvoiceStatus.RECONCILED;
        updateInvoiceStatus(invoice, newStatus);

        // S3b: the applied advance crosses the asset against the invoice payable
        // (debit 2205 / credit 133005). Publish only after every write has completed.
        eventPublisher.publishEvent(new SupplierAdvanceAppliedEvent(
                this, savedApplication.id(), savedApplication.advancePaymentId(),
                savedApplication.invoiceId(), savedApplication.appliedAmount(),
                savedApplication.applicationDate()));

        return AdvanceResponse.from(updatedAdvance);
    }

    /** Re-loads the persisted application when the repository save does not return its id. */
    private AdvanceApplication reloadApplication(AdvanceApplication application) {
        return advanceApplicationRepository.findByAdvancePaymentId(application.advancePaymentId())
                .stream()
                .filter(a -> application.invoiceId().equals(a.invoiceId())
                        && a.appliedAmount().compareTo(application.appliedAmount()) == 0)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new BusinessException(
                        "ADVANCE_APPLICATION_NOT_PERSISTED",
                        "No se pudo recuperar la aplicación del anticipo."));
    }

    /**
     * Mirrors the CxP-derived status onto the invoice: PAID when the payable is fully settled,
     * RECONCILED while it still has outstanding.
     */
    private void updateInvoiceStatus(SupplierInvoice invoice, InvoiceStatus status) {
        if (invoice.status() == status) {
            return;
        }
        var updatedInvoice = new SupplierInvoice(
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
        invoiceRepository.save(updatedInvoice);
    }
}
