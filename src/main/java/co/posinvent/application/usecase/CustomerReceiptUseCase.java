package co.posinvent.application.usecase;

import co.posinvent.application.dto.CustomerReceiptRequest;
import co.posinvent.application.dto.CustomerReceiptResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.CashReceiptEvent;
import co.posinvent.domain.model.CustomerReceipt;
import co.posinvent.domain.model.ReceiptApplication;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.CustomerReceiptRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class CustomerReceiptUseCase {

    private final CustomerReceiptRepository receiptRepo;
    private final AccountsReceivableUseCase arUseCase;
    private final ThirdPartyRepository thirdPartyRepo;
    private final ApplicationEventPublisher eventPublisher;

    public CustomerReceiptUseCase(
            CustomerReceiptRepository receiptRepo,
            AccountsReceivableUseCase arUseCase,
            ThirdPartyRepository thirdPartyRepo,
            ApplicationEventPublisher eventPublisher
    ) {
        this.receiptRepo = receiptRepo;
        this.arUseCase = arUseCase;
        this.thirdPartyRepo = thirdPartyRepo;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CustomerReceiptResponse create(CustomerReceiptRequest request) {
        // 1. Validate amount > 0
        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("CR_INVALID_AMOUNT",
                    "El monto del recibo debe ser mayor a cero");
        }

        // 2. Validate total applied == amount
        var appliedSum = request.applications().stream()
                .map(CustomerReceiptRequest.ArApplicationInput::appliedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var diff = request.amount().subtract(appliedSum).abs();
        if (diff.compareTo(new BigDecimal("0.01")) > 0) {
            throw new BusinessException("CR_AMOUNT_MISMATCH",
                    "La suma de montos aplicados (" + appliedSum
                    + ") no coincide con el total del recibo (" + request.amount() + ")");
        }

        // 3. Validate client exists
        thirdPartyRepo.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", request.clientId()));

        // 4. Apply each payment to CxC entries
        for (var app : request.applications()) {
            arUseCase.applyPayment(app.arId(), app.appliedAmount());
        }

        // 5. Build domain model
        var applications = request.applications().stream()
                .map(a -> new ReceiptApplication(null, null, a.arId(), a.appliedAmount()))
                .toList();

        var receipt = new CustomerReceipt(
                null,
                request.clientId(),
                request.amount(),
                request.paymentDate(),
                CustomerReceipt.PaymentMethod.valueOf(request.method()),
                request.reference(),
                request.notes(),
                null, // createdBy — set from controller if needed
                null, // createdAt — set by DB
                applications
        );

        var saved = receiptRepo.save(receipt);

        // C3: publish the collection so the accounting listener posts cash/receivable after commit.
        // The receipt carries no sequential number, so its reference is used when present, else its id.
        eventPublisher.publishEvent(new CashReceiptEvent(
                this, saved.id(), receiptNumber(saved), saved.amount(), saved.paymentDate()));

        return CustomerReceiptResponse.from(saved);
    }

    /** Human-readable identifier for the journal description: the user reference, else the receipt id. */
    private String receiptNumber(CustomerReceipt receipt) {
        return receipt.reference() != null && !receipt.reference().isBlank()
                ? receipt.reference()
                : receipt.id().toString();
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerReceiptResponse> listAll(Pageable pageable) {
        return PageResponse.from(
                receiptRepo.findAll(pageable),
                r -> enrich(CustomerReceiptResponse.from(r))
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerReceiptResponse> listByClient(UUID clientId, Pageable pageable) {
        return PageResponse.from(
                receiptRepo.findByClientId(clientId, pageable),
                r -> enrich(CustomerReceiptResponse.from(r))
        );
    }

    @Transactional(readOnly = true)
    public CustomerReceiptResponse getById(UUID id) {
        return receiptRepo.findById(id)
                .map(CustomerReceiptResponse::from)
                .map(this::enrich)
                .orElseThrow(() -> new ResourceNotFoundException("Recibo", id));
    }

    private CustomerReceiptResponse enrich(CustomerReceiptResponse r) {
        String clientName = null;
        if (r.clientId() != null) {
            clientName = thirdPartyRepo.findById(r.clientId())
                    .map(ThirdParty::name)
                    .orElse(null);
        }
        if (clientName != null) {
            return new CustomerReceiptResponse(
                    r.id(), r.clientId(), clientName,
                    r.amount(), r.paymentDate(), r.method(),
                    r.reference(), r.notes(), r.createdBy(), r.createdAt(),
                    r.applications()
            );
        }
        return r;
    }
}
