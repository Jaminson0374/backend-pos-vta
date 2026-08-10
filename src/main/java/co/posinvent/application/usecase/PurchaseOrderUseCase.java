package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.application.dto.PurchaseOrderRequest;
import co.posinvent.application.dto.PurchaseOrderResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.PurchaseLineItem;
import co.posinvent.domain.model.PurchaseOrder;
import co.posinvent.domain.model.PurchaseOrderStatus;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.repository.PurchaseOrderRepository;
import co.posinvent.domain.repository.ThirdPartyCategoryRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.domain.repository.WarehouseRepository;
import co.posinvent.domain.service.PurchaseOrderDomainService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PurchaseOrderUseCase {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final ThirdPartyCategoryRepository thirdPartyCategoryRepository;
    private final WarehouseRepository warehouseRepository;
    private final PurchaseOrderDomainService domainService;

    public PurchaseOrderUseCase(
            PurchaseOrderRepository purchaseOrderRepository,
            ThirdPartyRepository thirdPartyRepository,
            ThirdPartyCategoryRepository thirdPartyCategoryRepository,
            WarehouseRepository warehouseRepository
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.thirdPartyRepository = thirdPartyRepository;
        this.thirdPartyCategoryRepository = thirdPartyCategoryRepository;
        this.warehouseRepository = warehouseRepository;
        this.domainService = new PurchaseOrderDomainService();
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(Pageable pageable) {
        return PageResponse.from(
                purchaseOrderRepository.findAll(pageable)
                        .map(PurchaseOrderResponse::from)
                        .map(this::enrichSupplierName),
                r -> r
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> findByStatus(PurchaseOrderStatus status, Pageable pageable) {
        return PageResponse.from(
                purchaseOrderRepository.findByStatus(status, pageable)
                        .map(PurchaseOrderResponse::from)
                        .map(this::enrichSupplierName),
                r -> r
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> findBySupplier(UUID supplierId, Pageable pageable) {
        return PageResponse.from(
                purchaseOrderRepository.findBySupplierId(supplierId, pageable)
                        .map(PurchaseOrderResponse::from)
                        .map(this::enrichSupplierName),
                r -> r
        );
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getById(UUID id) {
        return purchaseOrderRepository.findById(id)
                .map(PurchaseOrderResponse::from)
                .map(this::enrichSupplierName)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> search(String q, Pageable pageable) {
        var searchPattern = q != null && !q.isBlank() ? "%" + q.toLowerCase() + "%" : q;
        return PageResponse.from(
                purchaseOrderRepository.search(searchPattern, pageable)
                        .map(PurchaseOrderResponse::from)
                        .map(this::enrichSupplierName),
                r -> r
        );
    }

    @Auditable(entityType = "PURCHASE_ORDER", action = "CREATE")
    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest request, UUID operatorId) {
        var supplier = thirdPartyRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", request.supplierId()));

        if (!isSupplier(supplier)) {
            throw new BusinessException("OC_NOT_A_SUPPLIER",
                    "El tercero seleccionado no es un proveedor. Tipo: " + supplier.type());
        }

        if (request.buyerId() != null) {
            var buyer = thirdPartyRepository.findById(request.buyerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comprador", request.buyerId()));
            if (buyer.type() != ThirdParty.ThirdPartyType.EMPLOYEE) {
                throw new IllegalArgumentException("El comprador debe ser un empleado");
            }
        }

        var dueDate = request.dueDate() != null
                ? request.dueDate()
                : request.orderDate().plusDays(supplier.creditDays());

        var lineItems = buildLineItems(null, request);
        var docNumber = generateDocumentNumber();

        var order = new PurchaseOrder(
                null,
                request.supplierId(),
                PurchaseOrderStatus.PENDING,
                request.orderDate(),
                docNumber,
                request.notes(),
                dueDate,
                request.buyerId(),
                request.paymentMethod(),
                request.supportDocumentType(),
                request.supportDocumentNumber(),
                request.currency(),
                operatorId,
                null,
                null,
                null,
                lineItems
        );

        return PurchaseOrderResponse.from(purchaseOrderRepository.save(order));
    }

    private String generateDocumentNumber() {
        var today = java.time.LocalDate.now();
        var prefix = "OC-" + today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        var last = purchaseOrderRepository.findFirstByDocumentNumberStartingWith(prefix);
        var seq = last.map(po -> {
            var parts = po.documentNumber().split("-");
            return Integer.parseInt(parts[parts.length - 1]) + 1;
        }).orElse(1);
        return prefix + String.format("%04d", seq);
    }

    @Auditable(entityType = "PURCHASE_ORDER", action = "UPDATE")
    @Transactional
    public PurchaseOrderResponse update(UUID id, PurchaseOrderRequest request) {
        var existing = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra", id));

        if (existing.status() != PurchaseOrderStatus.PENDING) {
            throw new BusinessException("OC_IMMUTABLE",
                    "Solo se pueden modificar órdenes en estado PENDING. " +
                    "Estado actual: " + existing.status());
        }

        var supplier = thirdPartyRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", request.supplierId()));

        // Validate all warehouses exist
        for (var line : request.lines()) {
            warehouseRepository.findById(line.warehouseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bodega", line.warehouseId()));
        }

        if (request.buyerId() != null) {
            var buyer = thirdPartyRepository.findById(request.buyerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comprador", request.buyerId()));
            if (buyer.type() != ThirdParty.ThirdPartyType.EMPLOYEE) {
                throw new IllegalArgumentException("El comprador debe ser un empleado");
            }
        }

        var dueDate = request.dueDate() != null
                ? request.dueDate()
                : request.orderDate().plusDays(supplier.creditDays());

        var lineItems = buildLineItems(id, request);

        var updated = new PurchaseOrder(
                existing.id(),
                request.supplierId(),
                existing.status(),
                request.orderDate(),
                existing.documentNumber(),
                request.notes(),
                dueDate,
                request.buyerId(),
                request.paymentMethod(),
                request.supportDocumentType(),
                request.supportDocumentNumber(),
                request.currency(),
                existing.createdBy(),
                existing.createdAt(),
                null,
                existing.version(),
                lineItems
        );

        return PurchaseOrderResponse.from(purchaseOrderRepository.save(updated));
    }

    @Auditable(entityType = "PURCHASE_ORDER", action = "DELETE")
    @Transactional
    public PurchaseOrderResponse cancel(UUID id) {
        var existing = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra", id));

        domainService.validateTransition(existing.status(), PurchaseOrderStatus.CANCELLED);

        var cancelled = new PurchaseOrder(
                existing.id(),
                existing.supplierId(),
                PurchaseOrderStatus.CANCELLED,
                existing.orderDate(),
                existing.documentNumber(),
                existing.notes(),
                existing.dueDate(),
                existing.buyerId(),
                existing.paymentMethod(),
                existing.supportDocumentType(),
                existing.supportDocumentNumber(),
                existing.currency(),
                existing.createdBy(),
                existing.createdAt(),
                null,
                existing.version(),
                existing.lines()
        );

        return PurchaseOrderResponse.from(purchaseOrderRepository.save(cancelled));
    }

    private List<PurchaseLineItem> buildLineItems(UUID ocId, PurchaseOrderRequest request) {
        var counter = new AtomicInteger(1);
        return request.lines().stream()
                .map(line -> {
                    domainService.validateLineItem(line.orderedQty());
                    return new PurchaseLineItem(
                            null,
                            ocId,
                            line.productId(),
                            line.warehouseId(),
                            line.orderedQty(),
                            java.math.BigDecimal.ZERO,
                            line.unitCost(),
                            line.discountPct() != null ? line.discountPct() : java.math.BigDecimal.ZERO,
                            line.taxType() != null ? line.taxType() : "EXENTO",
                            counter.getAndIncrement()
                    );
                })
                .toList();
    }

    private boolean isSupplier(ThirdParty tp) {
        if (tp.type() == ThirdParty.ThirdPartyType.SUPPLIER ||
            tp.type() == ThirdParty.ThirdPartyType.BOTH) {
            return true;
        }
        if (tp.thirdPartyCategoryId() != null) {
            return thirdPartyCategoryRepository.findById(tp.thirdPartyCategoryId())
                    .map(cat -> "SUPPLIER".equals(cat.baseType()) || "BOTH".equals(cat.baseType()))
                    .orElse(false);
        }
        return false;
    }

    private PurchaseOrderResponse enrichSupplierName(PurchaseOrderResponse r) {
        var enriched = r;
        if (r.supplierId() != null) {
            enriched = thirdPartyRepository.findById(r.supplierId())
                    .map(tp -> new PurchaseOrderResponse(
                            r.id(), r.supplierId(), tp.name(), r.status(), r.orderDate(),
                            r.documentNumber(), r.notes(), r.dueDate(), r.buyerId(), r.buyerName(),
                            r.paymentMethod(), r.supportDocumentType(), r.supportDocumentNumber(),
                            r.currency(), r.createdBy(), r.createdAt(), r.lines(),
                            r.taxTotal(), r.discountTotal(), r.grandTotal()))
                    .orElse(r);
        }
        if (enriched.buyerId() != null) {
            final var current = enriched;
            enriched = thirdPartyRepository.findById(current.buyerId())
                    .map(tp -> new PurchaseOrderResponse(
                            current.id(), current.supplierId(), current.supplierName(),
                            current.status(), current.orderDate(),
                            current.documentNumber(), current.notes(), current.dueDate(),
                            current.buyerId(), tp.name(),
                            current.paymentMethod(), current.supportDocumentType(),
                            current.supportDocumentNumber(),
                            current.currency(), current.createdBy(), current.createdAt(),
                            current.lines(),
                            current.taxTotal(), current.discountTotal(), current.grandTotal()))
                    .orElse(current);
        }
        return enriched;
    }
}
