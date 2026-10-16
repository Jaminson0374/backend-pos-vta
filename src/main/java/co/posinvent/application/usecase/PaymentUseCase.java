package co.posinvent.application.usecase;

import co.posinvent.application.dto.AdvanceRequest;
import co.posinvent.application.dto.AdvanceResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.PaymentRequest;
import co.posinvent.application.dto.PaymentResponse;
import co.posinvent.application.service.SupplierBalanceService;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.InvoiceStatus;
import co.posinvent.domain.model.Payment;
import co.posinvent.domain.model.Payment.InvoicePayment;
import co.posinvent.domain.model.SupplierInvoice;
import co.posinvent.domain.model.SupplierPaymentEvent;
import co.posinvent.domain.repository.PaymentRepository;
import co.posinvent.domain.repository.SupplierInvoiceRepository;
import co.posinvent.domain.service.PaymentDomainService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final SupplierInvoiceRepository invoiceRepository;
    private final PaymentDomainService domainService;
    private final SupplierBalanceService supplierBalanceService;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentUseCase(
            PaymentRepository paymentRepository,
            SupplierInvoiceRepository invoiceRepository,
            SupplierBalanceService supplierBalanceService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.domainService = new PaymentDomainService();
        this.supplierBalanceService = supplierBalanceService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> list(Pageable pageable) {
        return PageResponse.from(
                paymentRepository.findAll(pageable),
                PaymentResponse::from
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> findBySupplier(UUID supplierId, Pageable pageable) {
        return PageResponse.from(
                paymentRepository.findBySupplierId(supplierId, pageable),
                PaymentResponse::from
        );
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(UUID id) {
        return paymentRepository.findById(id)
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Pago", id));
    }

    @Transactional
    public PaymentResponse create(PaymentRequest request, UUID operatorId) {
        // 1. Validate payment amount > 0
        domainService.validateAmount(request.amount());

        // 2. Load all invoices referenced in the payment
        var invoiceIds = request.invoicePayments().stream()
                .map(PaymentRequest.InvoicePaymentInput::invoiceId)
                .toList();

        var invoices = invoiceIds.stream()
                .map(id -> invoiceRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Factura de proveedor", id)))
                .toList();

        // 3. Validate invoice payability
        var domainInvoicePayments = request.invoicePayments().stream()
                .map(ip -> new InvoicePayment(ip.invoiceId(), ip.appliedAmount()))
                .toList();

        domainService.validateInvoicePayments(invoices, domainInvoicePayments, request.amount());

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

        // 5. Update each SupplierInvoice status
        var invoiceByAppliedMap = request.invoicePayments().stream()
                .collect(Collectors.toMap(
                        PaymentRequest.InvoicePaymentInput::invoiceId,
                        PaymentRequest.InvoicePaymentInput::appliedAmount
                ));

        for (var invoice : invoices) {
            var appliedAmount = invoiceByAppliedMap.get(invoice.id());
            var newStatus = appliedAmount.compareTo(invoice.total()) >= 0
                    ? InvoiceStatus.PAID
                    : InvoiceStatus.RECONCILED;

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
                    newStatus,
                    invoice.notes(),
                    invoice.createdBy(),
                    invoice.createdAt(),
                    null,
                    invoice.version(),
                    invoice.ocIds()
            );
            invoiceRepository.save(updatedInvoice);
        }

        // 6. Update ThirdParty.currentBalance via shared service
        supplierBalanceService.updateSupplierBalance(request.supplierId(), request.amount(), true);

        return PaymentResponse.from(savedPayment);
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

        // 3. Update supplier balance (advance reduces debt)
        supplierBalanceService.updateSupplierBalance(request.supplierId(), request.amount(), true);

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
