package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.ElectronicInvoiceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.*;

@Repository
class ElectronicInvoiceRepositoryAdapter implements ElectronicInvoiceRepository {

    private final ElectronicInvoiceJpaRepository jpa;
    private final DianSyncQueueJpaRepository syncJpa;
    private final ElectronicInvoiceMapper mapper;
    private final DianSyncQueueMapper syncMapper;
    private final SalesDocumentJpaRepository salesDocJpa;
    private final ObjectMapper objectMapper;

    ElectronicInvoiceRepositoryAdapter(
            ElectronicInvoiceJpaRepository jpa,
            DianSyncQueueJpaRepository syncJpa,
            ElectronicInvoiceMapper mapper,
            DianSyncQueueMapper syncMapper,
            SalesDocumentJpaRepository salesDocJpa,
            ObjectMapper objectMapper
    ) {
        this.jpa = jpa;
        this.syncJpa = syncJpa;
        this.mapper = mapper;
        this.syncMapper = syncMapper;
        this.salesDocJpa = salesDocJpa;
        this.objectMapper = objectMapper;
    }

    @Override
    public ElectronicInvoice save(ElectronicInvoice domain) {
        var entity = domain.id() != null
                ? jpa.findById(domain.id()).orElse(new ElectronicInvoiceEntity())
                : new ElectronicInvoiceEntity();

        var salesDoc = salesDocJpa.findById(domain.salesDocumentId())
                .orElseThrow(() -> new RuntimeException("Sales document not found: " + domain.salesDocumentId()));
        entity.setSalesDocument(salesDoc);

        if (domain.sourceDocumentId() != null) {
            salesDocJpa.findById(domain.sourceDocumentId())
                    .ifPresent(entity::setSourceDocument);
        } else {
            entity.setSourceDocument(null);
        }

        entity.setCufe(domain.cufe());
        entity.setQrCode(domain.qrCode());
        entity.setProviderInvoiceId(domain.providerInvoiceId());
        entity.setPdfUrl(domain.pdfUrl());
        entity.setXmlUrl(domain.xmlUrl());
        entity.setProviderErrors(toJson(domain.providerErrors()));
        entity.setProviderResponse(toJson(domain.providerResponse()));
        entity.setStatus(domain.status());
        entity.setSentAt(domain.sentAt());
        entity.setResponseAt(domain.responseAt());

        var saved = jpa.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<ElectronicInvoice> findById(UUID id) {
        return jpa.findById(id).map(this::toDomain);
    }

    @Override
    public List<ElectronicInvoice> findAll(int page, int size) {
        return jpa.findAll(PageRequest.of(page, size)).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public Optional<ElectronicInvoice> findBySalesDocumentId(UUID salesDocumentId) {
        return jpa.findBySalesDocumentId(salesDocumentId).map(this::toDomain);
    }

    @Override
    public Optional<ElectronicInvoice> findByCufe(String cufe) {
        return jpa.findByCufe(cufe).map(this::toDomain);
    }

    @Override
    public List<ElectronicInvoice> findByStatus(ElectronicInvoiceStatus status, int page, int size) {
        return jpa.findByStatus(status).stream()
                .map(this::toDomain).toList();
    }

    @Override
    public long countByStatus(ElectronicInvoiceStatus status) {
        return jpa.countByStatus(status);
    }

    @Override
    public long countIssuedToday() {
        return jpa.countIssuedToday();
    }

    @Override
    public DianSyncQueueItem saveSyncQueueItem(DianSyncQueueItem item) {
        var entity = item.id() != null
                ? syncJpa.findById(item.id()).orElse(new DianSyncQueueItemEntity())
                : new DianSyncQueueItemEntity();

        var invEntity = jpa.findById(item.electronicInvoiceId())
                .orElseThrow(() -> new RuntimeException("Electronic invoice not found: " + item.electronicInvoiceId()));
        entity.setElectronicInvoice(invEntity);
        entity.setAttemptCount(item.attemptCount());
        entity.setMaxAttempts(item.maxAttempts());
        entity.setNextAttemptAt(item.nextAttemptAt());
        entity.setLastError(item.lastError());
        entity.setStatus(item.status());
        var saved = syncJpa.save(entity);
        return syncMapper.toDomain(saved);
    }

    @Override
    public void updateSyncQueueItem(DianSyncQueueItem item) {
        var entity = syncJpa.findById(item.id())
                .orElseThrow(() -> new RuntimeException("Sync queue item not found: " + item.id()));

        entity.setAttemptCount(item.attemptCount());
        entity.setMaxAttempts(item.maxAttempts());
        entity.setNextAttemptAt(item.nextAttemptAt());
        entity.setLastError(item.lastError());
        entity.setStatus(item.status());
        syncJpa.save(entity);
    }

    @Override
    public List<DianSyncQueueItem> findPendingSyncItems() {
        return syncJpa.findByStatusAndNextAttemptAtBefore(SyncStatus.PENDING, OffsetDateTime.now())
                .stream().map(syncMapper::toDomain).toList();
    }

    @Override
    public Optional<DianSyncQueueItem> findSyncQueueItemByInvoiceId(UUID invoiceId) {
        return syncJpa.findByElectronicInvoiceId(invoiceId).map(syncMapper::toDomain);
    }

    private ElectronicInvoice toDomain(ElectronicInvoiceEntity entity) {
        UUID sourceId = entity.getSourceDocument() != null ? entity.getSourceDocument().getId() : null;
        Map<String, Object> providerResponse = fromJson(entity.getProviderResponse());
        return new ElectronicInvoice(
                entity.getId(),
                entity.getSalesDocument().getId(),
                sourceId,
                entity.getCufe(),
                entity.getQrCode(),
                providerResponse,
                entity.getStatus(),
                entity.getSentAt(),
                entity.getResponseAt(),
                entity.getCreatedAt(),
                entity.getProviderInvoiceId(),
                entity.getPdfUrl(),
                entity.getXmlUrl(),
                fromJsonList(entity.getProviderErrors())
        );
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<ProviderError> fromJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ProviderError>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
