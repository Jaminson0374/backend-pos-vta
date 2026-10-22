package co.posinvent.application.service;

import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.ElectronicInvoiceProvider;
import co.posinvent.domain.repository.ElectronicInvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class ElectronicInvoiceJob {

    private static final Logger log = LoggerFactory.getLogger(ElectronicInvoiceJob.class);

    private final ElectronicInvoiceRepository repository;
    private final ElectronicInvoiceProvider provider;
    private final ElectronicInvoicePayloadService payloadService;

    public ElectronicInvoiceJob(
            ElectronicInvoiceRepository repository,
            ElectronicInvoiceProvider provider,
            ElectronicInvoicePayloadService payloadService
    ) {
        this.repository = repository;
        this.provider = provider;
        this.payloadService = payloadService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvoiceIssued(InvoiceIssuedEvent event) {
        log.info("Processing DIAN electronic invoice for sales document: {}", event.salesDocumentId());
        processInvoice(event.salesDocumentId(), event.invoiceNumber(), null);
    }

    @Async
    public void processInvoice(java.util.UUID salesDocumentId, String documentNumber, java.util.UUID sourceDocumentId) {
        try {
            // Create electronic invoice record
            var invoice = repository.save(new ElectronicInvoice(
                    null,
                    salesDocumentId,
                    sourceDocumentId,
                    null,
                    null,
                    null,
                    ElectronicInvoiceStatus.PENDING_SEND,
                    null,
                    null,
                    null
            ));

            // Enqueue to sync queue
            repository.saveSyncQueueItem(new DianSyncQueueItem(
                    null,
                    invoice.id(),
                    0,
                    5,
                    OffsetDateTime.now(),
                    null,
                    SyncStatus.PENDING,
                    null
            ));

            // Attempt to send immediately
            try {
                // Build the real payload from persisted sales data. The read-only transaction lives
                // inside the payload service, so the provider call below stays outside any transaction.
                var request = payloadService.build(salesDocumentId, documentNumber);

                var response = provider.sendInvoice(request);

                // Update electronic invoice with results
                repository.save(new ElectronicInvoice(
                        invoice.id(),
                        invoice.salesDocumentId(),
                        invoice.sourceDocumentId(),
                        response.cufe(),
                        response.qrCode(),
                        providerResponse(response),
                        statusOf(response),
                        OffsetDateTime.now(),
                        OffsetDateTime.now(),
                        invoice.createdAt(),
                        response.providerInvoiceId(),
                        response.pdfUrl(),
                        response.xmlUrl(),
                        response.errors()
                ));

                // Mark sync queue item as completed
                var syncItem = repository.findSyncQueueItemByInvoiceId(invoice.id());
                syncItem.ifPresent(item -> {
                    repository.updateSyncQueueItem(new DianSyncQueueItem(
                            item.id(), item.electronicInvoiceId(),
                            item.attemptCount() + 1, item.maxAttempts(),
                            item.nextAttemptAt(),
                            response.hasErrors() ? errorSummary(response) : null,
                            response.hasErrors() ? SyncStatus.FAILED : SyncStatus.COMPLETED,
                            item.createdAt()
                    ));
                });

                if (response.hasErrors()) {
                    log.warn("DIAN electronic invoice {} rejected by provider: {}",
                            invoice.id(), errorSummary(response));
                } else {
                    log.info("DIAN electronic invoice {} sent successfully. CUFE: {}",
                            invoice.id(), response.cufe());
                }
            } catch (Exception e) {
                log.error("Failed to send DIAN invoice {}: {}", invoice.id(), e.getMessage());
                handleSendFailure(invoice.id(), e.getMessage());
            }

        } catch (Exception e) {
            log.error("Failed to process DIAN invoice for sales document {}: {}", salesDocumentId, e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 30000)
    public void processQueue() {
        var pendingItems = repository.findPendingSyncItems();
        for (var item : pendingItems) {
            if (item.attemptCount() >= item.maxAttempts()) {
                repository.updateSyncQueueItem(new DianSyncQueueItem(
                        item.id(), item.electronicInvoiceId(),
                        item.attemptCount(), item.maxAttempts(),
                        item.nextAttemptAt(), item.lastError(),
                        SyncStatus.FAILED, item.createdAt()
                ));
                continue;
            }

            repository.updateSyncQueueItem(new DianSyncQueueItem(
                    item.id(), item.electronicInvoiceId(),
                    item.attemptCount(), item.maxAttempts(),
                    item.nextAttemptAt(), item.lastError(),
                    SyncStatus.IN_PROGRESS, item.createdAt()
            ));

            try {
                var invoice = repository.findById(item.electronicInvoiceId());
                if (invoice.isPresent() && invoice.get().cufe() != null) {
                    var response = provider.checkStatus(invoice.get().cufe());
                    var newStatus = "ACCEPTED_BY_DIAN".equals(response.status())
                            ? ElectronicInvoiceStatus.ACCEPTED_BY_DIAN
                            : ElectronicInvoiceStatus.REJECTED_BY_DIAN;

                    repository.save(new ElectronicInvoice(
                            invoice.get().id(), invoice.get().salesDocumentId(),
                            invoice.get().sourceDocumentId(), invoice.get().cufe(),
                            invoice.get().qrCode(), invoice.get().providerResponse(),
                            newStatus, invoice.get().sentAt(),
                            OffsetDateTime.now(), invoice.get().createdAt(),
                            invoice.get().providerInvoiceId(), invoice.get().pdfUrl(),
                            invoice.get().xmlUrl(), invoice.get().providerErrors()
                    ));

                    repository.updateSyncQueueItem(new DianSyncQueueItem(
                            item.id(), item.electronicInvoiceId(),
                            item.attemptCount() + 1, item.maxAttempts(),
                            item.nextAttemptAt(), null,
                            SyncStatus.COMPLETED, item.createdAt()
                    ));
                } else {
                    // Re-send with the real payload, rebuilt from the persisted document.
                    var request = payloadService.build(invoice.get().salesDocumentId(), null);
                    var response = provider.sendInvoice(request);

                    repository.save(new ElectronicInvoice(
                            invoice.get().id(), invoice.get().salesDocumentId(),
                            invoice.get().sourceDocumentId(), response.cufe(),
                            response.qrCode(),
                            Map.of("status", response.status(), "reference", response.providerReference()),
                            ElectronicInvoiceStatus.SENT, OffsetDateTime.now(),
                            null, invoice.get().createdAt()
                    ));
                }
            } catch (Exception e) {
                log.error("Queue processing failed for item {}: {}", item.id(), e.getMessage());
                handleSendFailure(item.electronicInvoiceId(), e.getMessage());
            }
        }
    }

    private static ElectronicInvoiceStatus statusOf(ElectronicInvoiceResponse response) {
        return switch (response.status()) {
            case "ACCEPTED_BY_DIAN" -> ElectronicInvoiceStatus.ACCEPTED_BY_DIAN;
            case "REJECTED_BY_DIAN" -> ElectronicInvoiceStatus.REJECTED_BY_DIAN;
            default -> ElectronicInvoiceStatus.SENT;
        };
    }

    private static Map<String, Object> providerResponse(ElectronicInvoiceResponse response) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("status", response.status());
        map.put("reference", response.providerReference());
        if (response.providerInvoiceId() != null) map.put("provider_invoice_id", response.providerInvoiceId());
        if (response.pdfUrl() != null) map.put("pdf_url", response.pdfUrl());
        if (response.xmlUrl() != null) map.put("xml_url", response.xmlUrl());
        if (!response.errors().isEmpty()) map.put("errors", response.errors());
        return map;
    }

    private static String errorSummary(ElectronicInvoiceResponse response) {
        return response.errors().stream()
                .map(e -> e.code() + ": " + e.message())
                .reduce((a, b) -> a + "; " + b)
                .orElse("El proveedor rechazó la factura");
    }

    private void handleSendFailure(java.util.UUID invoiceId, String errorMessage) {
        var syncItem = repository.findSyncQueueItemByInvoiceId(invoiceId);
        syncItem.ifPresent(item -> {
            var newAttemptCount = item.attemptCount() + 1;
            var delaySeconds = (long) Math.pow(2, newAttemptCount - 1) * 30; // 30s, 60s, 120s, 240s, 480s
            var nextAttempt = OffsetDateTime.now().plusSeconds(delaySeconds);
            var newStatus = newAttemptCount >= item.maxAttempts() ? SyncStatus.FAILED : SyncStatus.PENDING;

            repository.updateSyncQueueItem(new DianSyncQueueItem(
                    item.id(), item.electronicInvoiceId(),
                    newAttemptCount, item.maxAttempts(),
                    nextAttempt, errorMessage,
                    newStatus, item.createdAt()
            ));
        });
    }
}
