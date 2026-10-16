package co.posinvent.application.usecase;

import co.posinvent.application.dto.ElectronicInvoiceResponse;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.DianSyncQueueItem;
import co.posinvent.domain.model.ElectronicInvoiceStatus;
import co.posinvent.domain.model.SyncStatus;
import co.posinvent.domain.repository.ElectronicInvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ManageElectronicInvoiceUseCase {

    private final ElectronicInvoiceRepository repository;

    public ManageElectronicInvoiceUseCase(ElectronicInvoiceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ElectronicInvoiceResponse> list(String status, int page, int size) {
        if (status != null && !status.isBlank()) {
            return repository.findByStatus(ElectronicInvoiceStatus.valueOf(status), page, size)
                    .stream().map(ElectronicInvoiceResponse::from).toList();
        }
        return repository.findAll(page, size).stream()
                .map(ElectronicInvoiceResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ElectronicInvoiceResponse getById(UUID id) {
        return repository.findById(id)
                .map(ElectronicInvoiceResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Factura electrónica no encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public ElectronicInvoiceResponse getBySalesDocument(UUID salesDocumentId) {
        return repository.findBySalesDocumentId(salesDocumentId)
                .map(ElectronicInvoiceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Factura electrónica", salesDocumentId));
    }

    @Transactional
    public void retry(UUID id) {
        var invoice = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Factura electrónica no encontrada: " + id));

        var syncItem = repository.findSyncQueueItemByInvoiceId(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay item de sincronización para la factura: " + id));

        repository.updateSyncQueueItem(new DianSyncQueueItem(
                syncItem.id(), syncItem.electronicInvoiceId(),
                0, syncItem.maxAttempts(),
                OffsetDateTime.now(), null,
                SyncStatus.PENDING,
                syncItem.createdAt()
        ));
    }
}
