package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.*;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.*;
import co.posinvent.domain.service.FefoPicker;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ManageSalesDocumentUseCase {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SalesDocumentRepository documentRepo;
    private final SaleItemRepository itemRepo;
    private final ThirdPartyRepository thirdPartyRepo;
    private final StockRepository stockRepo;
    private final WarehouseRepository warehouseRepo;
    private final ProductRepository productRepo;
    private final AccountsReceivableUseCase accountsReceivableUseCase;
    private final FefoPicker fefoPicker;
    private final RecordMovementUseCase recordMovement;

    public ManageSalesDocumentUseCase(
            SalesDocumentRepository documentRepo,
            SaleItemRepository itemRepo,
            ThirdPartyRepository thirdPartyRepo,
            StockRepository stockRepo,
            WarehouseRepository warehouseRepo,
            ProductRepository productRepo,
            AccountsReceivableUseCase accountsReceivableUseCase,
            FefoPicker fefoPicker,
            RecordMovementUseCase recordMovement
    ) {
        this.documentRepo = documentRepo;
        this.itemRepo = itemRepo;
        this.thirdPartyRepo = thirdPartyRepo;
        this.stockRepo = stockRepo;
        this.warehouseRepo = warehouseRepo;
        this.productRepo = productRepo;
        this.accountsReceivableUseCase = accountsReceivableUseCase;
        this.fefoPicker = fefoPicker;
        this.recordMovement = recordMovement;
    }

    // ── Create ────────────────────────────────────────────────────────────

    @Auditable(entityType = "SALES_DOCUMENT", action = "CREATE")
    @Transactional
    public SalesDocumentResponse createDocument(SalesDocumentRequest request, UUID userId) {
        if (request.type() == SalesDocumentType.INVOICE) {
            throw new BusinessException("SD_INVOICE_MANUAL",
                    "Las facturas solo se generan desde el checkout del POS");
        }

        thirdPartyRepo.findById(request.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", request.clientId()));

        warehouseRepo.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", request.warehouseId()));

        var docNumber = generateDocumentNumber(request.type());

        var doc = new SalesDocument(
                null,
                request.type(),
                SalesDocumentStatus.DRAFT,
                docNumber,
                request.clientId(),
                request.warehouseId(),
                request.shiftId(),
                request.cashRegisterId(),
                request.sourceDocumentId(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                userId,
                null,
                null,
                List.of(),
                request.dueDate(),
                request.isCreditSale(),
                null
        );

        var saved = documentRepo.save(doc);
        return enrich(SalesDocumentResponse.from(saved));
    }

    // ── Transition (State Machine) ────────────────────────────────────────

    @Auditable(entityType = "SALES_DOCUMENT", action = "UPDATE")
    @Transactional
    public SalesDocumentResponse transitionDocument(UUID id, SalesDocumentStatus target) {
        var doc = documentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento de venta", id));

        validateTransition(doc, target);

        switch (target) {
            case CONFIRMED -> reserveStock(doc);
            case CANCELLED -> releaseStockIfNeeded(doc);
            case ISSUED -> {
                if (Boolean.TRUE.equals(doc.isCreditSale())) {
                    validateCreditOnIssue(doc);
                }
                // Decrement stock on ISSUED
                decrementStock(doc);
                // Auto-create CxC for credit sales
                if (Boolean.TRUE.equals(doc.isCreditSale())) {
                    accountsReceivableUseCase.createFromInvoice(doc);
                }
            }
        }

        var updated = new SalesDocument(
                doc.id(), doc.type(), target,
                doc.documentNumber(), doc.clientId(), doc.warehouseId(),
                doc.shiftId(), doc.cashRegisterId(), doc.sourceDocumentId(),
                doc.totalNet(), doc.totalTax0(), doc.totalTax5(), doc.totalTax8(), doc.totalTax19(),
                doc.totalAmount(), doc.createdBy(), doc.createdAt(), doc.updatedAt(),
                doc.items(), doc.dueDate(), doc.isCreditSale(), doc.reason()
        );

        var saved = documentRepo.save(updated);
        return enrich(SalesDocumentResponse.from(saved));
    }

    // ── Guard validation ──────────────────────────────────────────────────

    private void validateTransition(SalesDocument doc, SalesDocumentStatus target) {
        var current = doc.status();

        if (doc.type() == SalesDocumentType.QUOTE) {
            validateQuoteTransition(current, target, doc);
            return;
        }
        if (doc.type() == SalesDocumentType.ORDER) {
            validateOrderTransition(current, target, doc);
            return;
        }
        if (doc.type() == SalesDocumentType.INVOICE) {
            validateInvoiceTransition(current, target);
            return;
        }
        if (doc.type() == SalesDocumentType.CREDIT_NOTE || doc.type() == SalesDocumentType.DEBIT_NOTE) {
            validateInvoiceTransition(current, target);
            return;
        }

        throw illegalTransition(doc.type(), current, target);
    }

    private void validateQuoteTransition(SalesDocumentStatus current, SalesDocumentStatus target, SalesDocument doc) {
        if (target == SalesDocumentStatus.SENT) {
            if (current != SalesDocumentStatus.DRAFT)
                throw illegalTransition(SalesDocumentType.QUOTE, current, target);
            if (doc.items().isEmpty())
                throw new BusinessException("SD_NO_ITEMS",
                        "La cotización debe tener al menos un ítem para ser enviada");
            return;
        }
        if (target == SalesDocumentStatus.ACCEPTED || target == SalesDocumentStatus.REJECTED
                || target == SalesDocumentStatus.EXPIRED) {
            if (current != SalesDocumentStatus.SENT)
                throw illegalTransition(SalesDocumentType.QUOTE, current, target);
            return;
        }
        throw illegalTransition(SalesDocumentType.QUOTE, current, target);
    }

    private void validateOrderTransition(SalesDocumentStatus current, SalesDocumentStatus target, SalesDocument doc) {
        if (target == SalesDocumentStatus.CONFIRMED) {
            if (current != SalesDocumentStatus.DRAFT)
                throw illegalTransition(SalesDocumentType.ORDER, current, target);
            if (doc.items().isEmpty())
                throw new BusinessException("SD_NO_ITEMS",
                        "El pedido debe tener al menos un ítem para confirmar");
            return;
        }
        if (target == SalesDocumentStatus.CANCELLED) {
            if (current != SalesDocumentStatus.CONFIRMED && current != SalesDocumentStatus.DRAFT)
                throw illegalTransition(SalesDocumentType.ORDER, current, target);
            return;
        }
        if (target == SalesDocumentStatus.INVOICED || target == SalesDocumentStatus.PARTIALLY_INVOICED) {
            if (current != SalesDocumentStatus.CONFIRMED && current != SalesDocumentStatus.PARTIALLY_INVOICED)
                throw illegalTransition(SalesDocumentType.ORDER, current, target);
            return;
        }
        throw illegalTransition(SalesDocumentType.ORDER, current, target);
    }

    private void validateInvoiceTransition(SalesDocumentStatus current, SalesDocumentStatus target) {
        if (target == SalesDocumentStatus.ISSUED) {
            if (current != SalesDocumentStatus.DRAFT)
                throw illegalTransition(SalesDocumentType.INVOICE, current, target);
            return;
        }
        if (target == SalesDocumentStatus.PAID) {
            if (current != SalesDocumentStatus.ISSUED)
                throw illegalTransition(SalesDocumentType.INVOICE, current, target);
            return;
        }
        if (target == SalesDocumentStatus.CANCELLED) {
            if (current != SalesDocumentStatus.ISSUED)
                throw illegalTransition(SalesDocumentType.INVOICE, current, target);
            return;
        }
        throw illegalTransition(SalesDocumentType.INVOICE, current, target);
    }

    private BusinessException illegalTransition(SalesDocumentType type, SalesDocumentStatus from, SalesDocumentStatus to) {
        return new BusinessException("SD_INVALID_TRANSITION",
                "Transición inválida para " + type + ": " + from + " → " + to);
    }

    // ── Credit validation ─────────────────────────────────────────────────

    private void validateCreditOnIssue(SalesDocument doc) {
        var client = thirdPartyRepo.findById(doc.clientId())
                .orElseThrow(() -> new ResourceNotFoundException("Cliente", doc.clientId()));

        var creditLimit = client.creditLimit() != null
                ? client.creditLimit()
                : BigDecimal.ZERO;
        var currentBalance = client.currentBalance() != null
                ? client.currentBalance()
                : BigDecimal.ZERO;
        var docTotal = doc.totalAmount() != null
                ? doc.totalAmount()
                : BigDecimal.ZERO;

        if (creditLimit.compareTo(currentBalance.add(docTotal)) < 0) {
            throw new BusinessException("CREDIT_LIMIT_EXCEEDED",
                    "Excede cupo de crédito disponible. Límite: " + creditLimit
                    + ", Saldo actual: " + currentBalance
                    + ", Total documento: " + docTotal);
        }
    }

    // ── Stock reservation / release ───────────────────────────────────────

    private void reserveStock(SalesDocument doc) {
        for (var item : doc.items()) {
            var fallbackBatchId = item.batchId() != null ? item.batchId() : item.productId();
            stockRepo.findByProductBatchWarehouse(item.productId(), fallbackBatchId, doc.warehouseId())
                    .ifPresent(stock -> {
                        var updated = new InventoryStock(
                                stock.id(), stock.productId(), stock.batchId(), stock.warehouseId(),
                                stock.currentQuantity(),
                                stock.committedQuantity().add(item.quantity()),
                                stock.unitCost(), stock.createdAt(), stock.updatedAt()
                        );
                        stockRepo.save(updated);
                    });
        }
    }

    private void releaseStockIfNeeded(SalesDocument doc) {
        if (doc.type() != SalesDocumentType.ORDER) return;
        if (doc.status() != SalesDocumentStatus.CONFIRMED) return;

        for (var item : doc.items()) {
            var fallbackBatchId = item.batchId() != null ? item.batchId() : item.productId();
            stockRepo.findByProductBatchWarehouse(item.productId(), fallbackBatchId, doc.warehouseId())
                    .ifPresent(stock -> {
                        var newCommitted = stock.committedQuantity().subtract(item.quantity());
                        if (newCommitted.compareTo(BigDecimal.ZERO) < 0) newCommitted = BigDecimal.ZERO;
                        var updated = new InventoryStock(
                                stock.id(), stock.productId(), stock.batchId(), stock.warehouseId(),
                                stock.currentQuantity(), newCommitted,
                                stock.unitCost(), stock.createdAt(), stock.updatedAt()
                        );
                        stockRepo.save(updated);
                    });
        }
    }

    // ── Stock decrement on ISSUED ──────────────────────────────────────────

    private void decrementStock(SalesDocument doc) {
        for (var item : doc.items()) {
            // FEFO: pick batches by expiration date
            var allocations = fefoPicker.pick(item.productId(), doc.warehouseId(), item.quantity());

            // Decrement stock and record kardex movement for each batch consumed
            for (var alloc : allocations) {
                var stock = stockRepo.findByProductBatchWarehouse(
                        item.productId(), alloc.batchId(), doc.warehouseId())
                        .orElseThrow(() -> new ResourceNotFoundException("Stock", item.productId()));

                var previousQty = stock.currentQuantity();
                var newQty = previousQty.subtract(alloc.quantity());

                stockRepo.save(new InventoryStock(
                        stock.id(),
                        stock.productId(),
                        stock.batchId(),
                        stock.warehouseId(),
                        newQty,
                        stock.committedQuantity(),
                        stock.unitCost(),
                        stock.createdAt(),
                        null
                ));

                recordMovement.record(
                        item.productId(), alloc.batchId(), doc.warehouseId(),
                        MovementType.EXIT,
                        alloc.quantity(), alloc.unitCost(),
                        previousQty, newQty,
                        "SALE", doc.id(),
                        "Venta #" + doc.id()
                );
            }
        }
    }

    // ── Items ─────────────────────────────────────────────────────────────

    @Auditable(entityType = "SALES_DOCUMENT", action = "UPDATE")
    @Transactional
    public SalesDocumentResponse addItem(UUID documentId, SaleItemRequest request) {
        var doc = documentRepo.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento de venta", documentId));

        if (doc.status() != SalesDocumentStatus.DRAFT) {
            throw new BusinessException("SD_IMMUTABLE",
                    "Solo documentos en DRAFT pueden modificarse. Estado: " + doc.status());
        }

        var nextLine = doc.items().stream()
                .mapToInt(SaleItem::lineNumber)
                .max()
                .orElse(0) + 1;

        var taxRate = resolveTaxRate(request.taxType());
        var subtotal = request.quantity().multiply(request.unitPrice());
        var taxAmount = subtotal.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        var item = new SaleItem(
                null, documentId, request.productId(),
                request.quantity(), request.unitPrice(),
                request.taxType(), taxRate, taxAmount, subtotal,
                nextLine, null
        );

        itemRepo.save(item);

        recalculateDocumentTotals(documentId);

        return getById(documentId);
    }

    @Auditable(entityType = "SALES_DOCUMENT", action = "UPDATE")
    @Transactional
    public SalesDocumentResponse updateItem(UUID itemId, SaleItemRequest request) {
        var item = itemRepo.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Ítem de venta", itemId));

        var doc = documentRepo.findById(item.documentId())
                .orElseThrow(() -> new ResourceNotFoundException("Documento de venta", item.documentId()));

        if (doc.status() != SalesDocumentStatus.DRAFT) {
            throw new BusinessException("SD_IMMUTABLE",
                    "Solo documentos en DRAFT pueden modificarse. Estado: " + doc.status());
        }

        var taxRate = resolveTaxRate(request.taxType());
        var subtotal = request.quantity().multiply(request.unitPrice());
        var taxAmount = subtotal.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        var updated = new SaleItem(
                item.id(), item.documentId(), item.productId(),
                request.quantity(), request.unitPrice(),
                request.taxType(), taxRate, taxAmount, subtotal,
                item.lineNumber(), item.batchId()
        );

        itemRepo.save(updated);

        recalculateDocumentTotals(item.documentId());

        return getById(item.documentId());
    }

    @Auditable(entityType = "SALES_DOCUMENT", action = "DELETE")
    @Transactional
    public SalesDocumentResponse removeItem(UUID itemId) {
        var item = itemRepo.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Ítem de venta", itemId));

        var docId = item.documentId();
        var doc = documentRepo.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento de venta", docId));

        if (doc.status() != SalesDocumentStatus.DRAFT) {
            throw new BusinessException("SD_IMMUTABLE",
                    "Solo documentos en DRAFT pueden modificarse. Estado: " + doc.status());
        }

        itemRepo.deleteById(itemId);

        recalculateDocumentTotals(docId);

        return getById(docId);
    }

    // ── Queries ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<SalesDocumentResponse> getDocuments(
            int page, int size, SalesDocumentType type, SalesDocumentStatus status) {
        var sort = Sort.by(Sort.Direction.DESC, "createdAt");
        var pageable = PageRequest.of(page, size, sort);

        if (type != null && status != null) {
            return PageResponse.from(
                    documentRepo.findByTypeAndStatus(type, status, pageable)
                            .map(SalesDocumentResponse::from)
                            .map(this::enrich),
                    r -> r
            );
        }
        if (type != null) {
            return PageResponse.from(
                    documentRepo.findByType(type, pageable)
                            .map(SalesDocumentResponse::from)
                            .map(this::enrich),
                    r -> r
            );
        }
        if (status != null) {
            return PageResponse.from(
                    documentRepo.findByStatus(status, pageable)
                            .map(SalesDocumentResponse::from)
                            .map(this::enrich),
                    r -> r
            );
        }
        return PageResponse.from(
                documentRepo.findAll(pageable)
                        .map(SalesDocumentResponse::from)
                        .map(this::enrich),
                r -> r
        );
    }

    @Transactional(readOnly = true)
    public SalesDocumentResponse getById(UUID id) {
        var doc = documentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Documento de venta", id));
        return enrich(SalesDocumentResponse.from(doc));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private BigDecimal resolveTaxRate(String taxType) {
        return switch (taxType) {
            case "IVA_5" -> BigDecimal.valueOf(5);
            case "IVA_8" -> BigDecimal.valueOf(8);
            case "IVA_19" -> BigDecimal.valueOf(19);
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Recalculates document totals by summing all its items.
     * Follows the same pattern as PosCheckoutUseCase.calculateTotals().
     */
    private void recalculateDocumentTotals(UUID documentId) {
        var items = itemRepo.findByDocumentId(documentId);

        var net = BigDecimal.ZERO.setScale(2);
        var tax0 = BigDecimal.ZERO.setScale(2);
        var tax5 = BigDecimal.ZERO.setScale(2);
        var tax8 = BigDecimal.ZERO.setScale(2);
        var tax19 = BigDecimal.ZERO.setScale(2);

        for (var item : items) {
            net = net.add(item.subtotal());
            var rate = item.taxRate();
            var amount = item.taxAmount();
            if (rate.compareTo(BigDecimal.ZERO) == 0) {
                tax0 = tax0.add(amount);
            } else if (rate.compareTo(new BigDecimal("5")) == 0) {
                tax5 = tax5.add(amount);
            } else if (rate.compareTo(new BigDecimal("8")) == 0) {
                tax8 = tax8.add(amount);
            } else {
                tax19 = tax19.add(amount);
            }
        }

        var total = net.add(tax0).add(tax5).add(tax8).add(tax19);

        var doc = documentRepo.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento de venta", documentId));

        var updated = new SalesDocument(
                doc.id(), doc.type(), doc.status(),
                doc.documentNumber(), doc.clientId(), doc.warehouseId(),
                doc.shiftId(), doc.cashRegisterId(), doc.sourceDocumentId(),
                net, tax0, tax5, tax8, tax19, total,
                doc.createdBy(), doc.createdAt(), doc.updatedAt(),
                doc.items(), doc.dueDate(), doc.isCreditSale(), doc.reason()
        );

        documentRepo.save(updated);
    }

    private String generateDocumentNumber(SalesDocumentType type) {
        var today = LocalDate.now().format(DATE_FMT);
        var prefix = type.name() + "-" + today + "-";

        // Query for the max document number with this prefix
        var page = documentRepo.findAll(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "documentNumber")));

        int seq = 1;
        if (!page.getContent().isEmpty()) {
            var lastDoc = page.getContent().getFirst();
            if (lastDoc.documentNumber() != null && lastDoc.documentNumber().startsWith(prefix)) {
                var parts = lastDoc.documentNumber().split("-");
                if (parts.length == 4) {
                    try {
                        seq = Integer.parseInt(parts[3]) + 1;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return prefix + String.format("%04d", seq);
    }

    private SalesDocumentResponse enrich(SalesDocumentResponse r) {
        if (r.clientId() != null) {
            return thirdPartyRepo.findById(r.clientId())
                    .map(tp -> new SalesDocumentResponse(
                            r.id(), r.type(), r.status(), r.documentNumber(),
                            r.clientId(), tp.name(),
                            r.warehouseId(), r.shiftId(), r.cashRegisterId(), r.sourceDocumentId(),
                            r.totalNet(), r.totalTax0(), r.totalTax5(), r.totalTax8(), r.totalTax19(),
                            r.totalAmount(), r.createdBy(), r.createdAt(), r.updatedAt(),
                            r.items(), r.dueDate(), r.isCreditSale()
                    ))
                    .orElse(r);
        }
        return r;
    }
}
