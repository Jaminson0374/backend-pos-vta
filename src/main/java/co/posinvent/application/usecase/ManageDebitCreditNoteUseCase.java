package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.DebitCreditNoteRequest;
import co.posinvent.application.dto.DebitCreditNoteResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.service.SupplierBalanceService;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.DebitCreditNote;
import co.posinvent.domain.repository.DebitCreditNoteRepository;
import co.posinvent.domain.repository.SupplierInvoiceRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ManageDebitCreditNoteUseCase {

    private final DebitCreditNoteRepository repository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final SupplierInvoiceRepository supplierInvoiceRepository;
    private final SupplierBalanceService supplierBalanceService;
    private final AccountsPayableUseCase accountsPayableUseCase;

    public ManageDebitCreditNoteUseCase(
            DebitCreditNoteRepository repository,
            ThirdPartyRepository thirdPartyRepository,
            SupplierInvoiceRepository supplierInvoiceRepository,
            SupplierBalanceService supplierBalanceService,
            AccountsPayableUseCase accountsPayableUseCase
    ) {
        this.repository = repository;
        this.thirdPartyRepository = thirdPartyRepository;
        this.supplierInvoiceRepository = supplierInvoiceRepository;
        this.supplierBalanceService = supplierBalanceService;
        this.accountsPayableUseCase = accountsPayableUseCase;
    }

    @Transactional(readOnly = true)
    public PageResponse<DebitCreditNoteResponse> list(String type, UUID supplierId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        var notesPage = repository.findFiltered(type, supplierId, pageable);
        return PageResponse.from(notesPage, note -> {
            var supplier = thirdPartyRepository.findById(note.supplierId()).orElse(null);
            return DebitCreditNoteResponse.from(note, supplier != null ? supplier.name() : null);
        });
    }

    @Transactional(readOnly = true)
    public DebitCreditNoteResponse getById(UUID id) {
        var note = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nota débito/crédito", id));
        var supplier = thirdPartyRepository.findById(note.supplierId()).orElse(null);
        return DebitCreditNoteResponse.from(note, supplier != null ? supplier.name() : null);
    }

    @Auditable(entityType = "DEBIT_CREDIT_NOTE", action = "CREATE")
    @Transactional
    public DebitCreditNoteResponse create(DebitCreditNoteRequest request) {
        validateType(request.type());
        validateSupplierExists(request.supplierId());

        if (request.supplierInvoiceId() != null) {
            validateInvoiceExists(request.supplierInvoiceId());
        }

        var documentNumber = request.documentNumber() != null && !request.documentNumber().isBlank()
                ? request.documentNumber()
                : generateDocumentNumber();

        var now = OffsetDateTime.now();
        var note = new DebitCreditNote(
                null,
                request.type(),
                request.supplierId(),
                request.supplierInvoiceId(),
                documentNumber,
                request.amount(),
                request.reason(),
                request.reference(),
                null, // createdBy from security context, not in request
                now,
                now,
                0L
        );

        var saved = repository.save(note);

        // Update AccountsPayable for the linked invoice
        if (saved.supplierInvoiceId() != null) {
            accountsPayableUseCase.applyDebitNote(saved);
        }

        // Adjust supplier balance:
        // DEBIT_NOTE → increases debt → isCredit=false
        // CREDIT_NOTE → reduces debt → isCredit=true
        boolean isCredit = "CREDIT_NOTE".equals(saved.type());
        supplierBalanceService.updateSupplierBalance(saved.supplierId(), saved.amount(), isCredit);

        var supplier = thirdPartyRepository.findById(saved.supplierId()).orElse(null);
        return DebitCreditNoteResponse.from(saved, supplier != null ? supplier.name() : null);
    }

    @Auditable(entityType = "DEBIT_CREDIT_NOTE", action = "UPDATE")
    @Transactional
    public DebitCreditNoteResponse update(UUID id, DebitCreditNoteRequest request) {
        var existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nota débito/crédito", id));

        validateType(request.type());
        validateSupplierExists(request.supplierId());

        if (request.supplierInvoiceId() != null) {
            validateInvoiceExists(request.supplierInvoiceId());
        }

        // Reverse the old balance effect
        boolean oldIsCredit = "CREDIT_NOTE".equals(existing.type());
        supplierBalanceService.updateSupplierBalance(existing.supplierId(), existing.amount(), !oldIsCredit);

        // Apply the new balance effect
        boolean newIsCredit = "CREDIT_NOTE".equals(request.type());
        supplierBalanceService.updateSupplierBalance(request.supplierId(), request.amount(), newIsCredit);

        var documentNumber = request.documentNumber() != null && !request.documentNumber().isBlank()
                ? request.documentNumber()
                : existing.documentNumber();

        var updated = new DebitCreditNote(
                id,
                request.type(),
                request.supplierId(),
                request.supplierInvoiceId(),
                documentNumber,
                request.amount(),
                request.reason(),
                request.reference(),
                existing.createdBy(),
                existing.createdAt(),
                OffsetDateTime.now(),
                existing.version()
        );

        var saved = repository.save(updated);

        // Update AccountsPayable for the linked invoice
        if (saved.supplierInvoiceId() != null) {
            accountsPayableUseCase.applyDebitNote(saved);
        }

        var supplier = thirdPartyRepository.findById(saved.supplierId()).orElse(null);
        return DebitCreditNoteResponse.from(saved, supplier != null ? supplier.name() : null);
    }

    @Auditable(entityType = "DEBIT_CREDIT_NOTE", action = "DELETE")
    @Transactional
    public void delete(UUID id) {
        var existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nota débito/crédito", id));

        // Reverse the balance effect:
        // DEBIT_NOTE had increased debt → undo with isCredit=true (reduces debt back)
        // CREDIT_NOTE had reduced debt → undo with isCredit=false (adds debt back)
        boolean isCredit = "CREDIT_NOTE".equals(existing.type());
        supplierBalanceService.updateSupplierBalance(existing.supplierId(), existing.amount(), !isCredit);

        repository.delete(id);
    }

    private String generateDocumentNumber() {
        long count = repository.findAll(PageRequest.of(0, 1)).getTotalElements();
        return String.format("NC-%06d", count + 1);
    }

    private void validateType(String type) {
        if (!"DEBIT_NOTE".equals(type) && !"CREDIT_NOTE".equals(type)) {
            throw new BusinessException(
                    "INVALID_NOTE_TYPE",
                    "type must be DEBIT_NOTE or CREDIT_NOTE"
            );
        }
    }

    private void validateSupplierExists(UUID supplierId) {
        if (thirdPartyRepository.findById(supplierId).isEmpty()) {
            throw new BusinessException("SUPPLIER_NOT_FOUND", "Proveedor no encontrado: " + supplierId);
        }
    }

    private void validateInvoiceExists(UUID invoiceId) {
        if (supplierInvoiceRepository.findById(invoiceId).isEmpty()) {
            throw new BusinessException("INVOICE_NOT_FOUND", "Factura no encontrada: " + invoiceId);
        }
    }
}
