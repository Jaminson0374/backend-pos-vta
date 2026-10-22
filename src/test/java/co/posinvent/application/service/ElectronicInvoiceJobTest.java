package co.posinvent.application.service;

import co.posinvent.domain.model.DianSyncQueueItem;
import co.posinvent.domain.model.ElectronicInvoice;
import co.posinvent.domain.model.ElectronicInvoiceRequest;
import co.posinvent.domain.model.ElectronicInvoiceResponse;
import co.posinvent.domain.model.ElectronicInvoiceStatus;
import co.posinvent.domain.model.ProviderError;
import co.posinvent.domain.model.SyncStatus;
import co.posinvent.domain.repository.ElectronicInvoiceProvider;
import co.posinvent.domain.repository.ElectronicInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct unit tests for {@link ElectronicInvoiceJob#processInvoice}. The job instance is built
 * directly, not through the Spring proxy, so the class-level {@code @Async} annotation does not
 * interfere with these synchronous calls.
 */
@ExtendWith(MockitoExtension.class)
class ElectronicInvoiceJobTest {

    @Mock private ElectronicInvoiceRepository repository;
    @Mock private ElectronicInvoiceProvider provider;
    @Mock private ElectronicInvoicePayloadService payloadService;

    private ElectronicInvoiceJob job;

    private static final UUID SALES_DOCUMENT_ID = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID SYNC_ITEM_ID = UUID.randomUUID();
    private static final String DOCUMENT_NUMBER = "FV-001";

    @BeforeEach
    void setUp() {
        job = new ElectronicInvoiceJob(repository, provider, payloadService);
    }

    @Test
    void sendsRealPayloadAndPersistsAcceptedInvoice() {
        var request = request();
        when(payloadService.build(SALES_DOCUMENT_ID, DOCUMENT_NUMBER)).thenReturn(request);
        when(repository.save(any(ElectronicInvoice.class)))
                .thenReturn(savedInvoice(ElectronicInvoiceStatus.PENDING_SEND));
        when(provider.sendInvoice(request))
                .thenReturn(new ElectronicInvoiceResponse("CUFE-1", "QR-1", "ACCEPTED_BY_DIAN", "REF-1"));
        when(repository.findSyncQueueItemByInvoiceId(INVOICE_ID))
                .thenReturn(Optional.of(syncItem(0, 5)));

        job.processInvoice(SALES_DOCUMENT_ID, DOCUMENT_NUMBER, null);

        var invoiceCaptor = ArgumentCaptor.forClass(ElectronicInvoice.class);
        verify(repository, times(2)).save(invoiceCaptor.capture());
        var created = invoiceCaptor.getAllValues().get(0);
        assertThat(created.status()).isEqualTo(ElectronicInvoiceStatus.PENDING_SEND);

        // The payload built by the payload service is what reaches the provider.
        verify(provider).sendInvoice(request);

        var persisted = invoiceCaptor.getAllValues().get(1);
        assertThat(persisted.status()).isEqualTo(ElectronicInvoiceStatus.ACCEPTED_BY_DIAN);
        assertThat(persisted.cufe()).isEqualTo("CUFE-1");
        assertThat(persisted.qrCode()).isEqualTo("QR-1");
        assertThat(persisted.providerResponse())
                .containsEntry("status", "ACCEPTED_BY_DIAN")
                .containsEntry("reference", "REF-1");

        verify(repository).saveSyncQueueItem(any(DianSyncQueueItem.class));

        var queueCaptor = ArgumentCaptor.forClass(DianSyncQueueItem.class);
        verify(repository).updateSyncQueueItem(queueCaptor.capture());
        assertThat(queueCaptor.getValue().status()).isEqualTo(SyncStatus.COMPLETED);
    }

    @Test
    void routesPayloadBuildFailureToRetryState() {
        when(payloadService.build(SALES_DOCUMENT_ID, DOCUMENT_NUMBER))
                .thenThrow(new IllegalStateException("no persisted data"));
        when(repository.save(any(ElectronicInvoice.class)))
                .thenReturn(savedInvoice(ElectronicInvoiceStatus.PENDING_SEND));
        when(repository.findSyncQueueItemByInvoiceId(INVOICE_ID))
                .thenReturn(Optional.of(syncItem(0, 5)));

        job.processInvoice(SALES_DOCUMENT_ID, DOCUMENT_NUMBER, null);

        verify(provider, never()).sendInvoice(any());

        var queueCaptor = ArgumentCaptor.forClass(DianSyncQueueItem.class);
        verify(repository).updateSyncQueueItem(queueCaptor.capture());
        assertThat(queueCaptor.getValue().lastError()).isEqualTo("no persisted data");
        assertThat(queueCaptor.getValue().status()).isEqualTo(SyncStatus.PENDING);
    }

    @Test
    void routesProviderExceptionToRetryState() {
        var request = request();
        when(payloadService.build(SALES_DOCUMENT_ID, DOCUMENT_NUMBER)).thenReturn(request);
        when(repository.save(any(ElectronicInvoice.class)))
                .thenReturn(savedInvoice(ElectronicInvoiceStatus.PENDING_SEND));
        when(provider.sendInvoice(request)).thenThrow(new RuntimeException("provider unavailable"));
        when(repository.findSyncQueueItemByInvoiceId(INVOICE_ID))
                .thenReturn(Optional.of(syncItem(0, 5)));

        job.processInvoice(SALES_DOCUMENT_ID, DOCUMENT_NUMBER, null);

        var queueCaptor = ArgumentCaptor.forClass(DianSyncQueueItem.class);
        verify(repository).updateSyncQueueItem(queueCaptor.capture());
        assertThat(queueCaptor.getValue().lastError()).isEqualTo("provider unavailable");
        assertThat(queueCaptor.getValue().status()).isEqualTo(SyncStatus.PENDING);
    }

    @Test
    void marksQueueFailedWhenAttemptsExhausted() {
        var request = request();
        when(payloadService.build(SALES_DOCUMENT_ID, DOCUMENT_NUMBER)).thenReturn(request);
        when(repository.save(any(ElectronicInvoice.class)))
                .thenReturn(savedInvoice(ElectronicInvoiceStatus.PENDING_SEND));
        when(provider.sendInvoice(request)).thenThrow(new RuntimeException("boom"));
        when(repository.findSyncQueueItemByInvoiceId(INVOICE_ID))
                .thenReturn(Optional.of(syncItem(4, 5)));

        job.processInvoice(SALES_DOCUMENT_ID, DOCUMENT_NUMBER, null);

        var queueCaptor = ArgumentCaptor.forClass(DianSyncQueueItem.class);
        verify(repository).updateSyncQueueItem(queueCaptor.capture());
        assertThat(queueCaptor.getValue().status()).isEqualTo(SyncStatus.FAILED);
    }

    @Test
    void persistsStructuredProviderErrorsAndFailsTheQueue() {
        var request = request();
        when(payloadService.build(SALES_DOCUMENT_ID, DOCUMENT_NUMBER)).thenReturn(request);
        when(repository.save(any(ElectronicInvoice.class)))
                .thenReturn(savedInvoice(ElectronicInvoiceStatus.PENDING_SEND));
        when(provider.sendInvoice(request)).thenReturn(new ElectronicInvoiceResponse(
                null, null, "REJECTED_BY_DIAN", "HTTP-400", null, null, null,
                List.of(new ProviderError("invalid_postal_code", "Código postal inválido",
                        List.of("customer.address.postal_code"), "detail"))));
        when(repository.findSyncQueueItemByInvoiceId(INVOICE_ID))
                .thenReturn(Optional.of(syncItem(0, 5)));

        job.processInvoice(SALES_DOCUMENT_ID, DOCUMENT_NUMBER, null);

        var invoiceCaptor = ArgumentCaptor.forClass(ElectronicInvoice.class);
        verify(repository, times(2)).save(invoiceCaptor.capture());
        var persisted = invoiceCaptor.getAllValues().get(1);
        assertThat(persisted.status()).isEqualTo(ElectronicInvoiceStatus.REJECTED_BY_DIAN);
        assertThat(persisted.providerErrors()).hasSize(1);
        assertThat(persisted.providerErrors().get(0).code()).isEqualTo("invalid_postal_code");

        var queueCaptor = ArgumentCaptor.forClass(DianSyncQueueItem.class);
        verify(repository).updateSyncQueueItem(queueCaptor.capture());
        assertThat(queueCaptor.getValue().status()).isEqualTo(SyncStatus.FAILED);
        assertThat(queueCaptor.getValue().lastError()).contains("invalid_postal_code");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ElectronicInvoiceRequest request() {
        return new ElectronicInvoiceRequest(
                DOCUMENT_NUMBER, "2026-10-01", "900123456", "Cliente Test",
                List.of(), Map.of(), new BigDecimal("120500"), null, null);
    }

    private ElectronicInvoice savedInvoice(ElectronicInvoiceStatus status) {
        return new ElectronicInvoice(
                INVOICE_ID, SALES_DOCUMENT_ID, null, null, null, null,
                status, null, null, OffsetDateTime.now());
    }

    private DianSyncQueueItem syncItem(int attemptCount, int maxAttempts) {
        return new DianSyncQueueItem(
                SYNC_ITEM_ID, INVOICE_ID, attemptCount, maxAttempts,
                OffsetDateTime.now(), null, SyncStatus.PENDING, OffsetDateTime.now());
    }
}
