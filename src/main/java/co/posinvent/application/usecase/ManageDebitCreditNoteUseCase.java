package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.DebitCreditNoteRequest;
import co.posinvent.application.dto.DebitCreditNoteResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.DebitCreditNote;
import co.posinvent.domain.model.SupplierNoteAccountedEvent;
import co.posinvent.domain.repository.DebitCreditNoteRepository;
import co.posinvent.domain.repository.SupplierInvoiceRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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
    private final AccountsPayableUseCase accountsPayableUseCase;
    private final ApplicationEventPublisher eventPublisher;

    public ManageDebitCreditNoteUseCase(
            DebitCreditNoteRepository repository,
            ThirdPartyRepository thirdPartyRepository,
            SupplierInvoiceRepository supplierInvoiceRepository,
            AccountsPayableUseCase accountsPayableUseCase,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.thirdPartyRepository = thirdPartyRepository;
        this.supplierInvoiceRepository = supplierInvoiceRepository;
        this.accountsPayableUseCase = accountsPayableUseCase;
        this.eventPublisher = eventPublisher;
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

        // C5: publish the accounting event so the note adjusts payable/cuenta puente.
        eventPublisher.publishEvent(new SupplierNoteAccountedEvent(
                this,
                saved.id(),
                saved.documentNumber(),
                saved.amount(),
                saved.isDebitNote(),
                saved.createdAt() != null ? saved.createdAt().toLocalDate() : LocalDate.now()
        ));

        // Update AccountsPayable for the linked invoice. The CxP is the single source of truth for
        // the payable side, so notes no longer write the static third_parties.current_balance.
        if (saved.supplierInvoiceId() != null) {
            accountsPayableUseCase.applyDebitNote(saved);
        }

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

        // M2: reverse the OLD note's AP effect before applying the NEW one, so an edit never
        // double-counts (a lost reversal) nor stacks both notes on the payable.
        if (existing.supplierInvoiceId() != null) {
            accountsPayableUseCase.reverseDebitNote(existing);
        }

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

        // Update AccountsPayable for the linked invoice (the new note's effect).
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

        // M2: deleting a note must reverse its AP effect on the CxP, not only the static
        // third_parties.current_balance (which is no longer written by this use case).
        if (existing.supplierInvoiceId() != null) {
            accountsPayableUseCase.reverseDebitNote(existing);
        }

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
