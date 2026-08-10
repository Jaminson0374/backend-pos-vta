package co.posinvent.application.usecase;

import co.posinvent.application.dto.BatchRequest;
import co.posinvent.application.dto.BatchResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.model.BatchType;
import co.posinvent.domain.model.InventoryMovement;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.repository.*;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class BatchUseCase {

    private final BatchRepository          batchRepository;
    private final ThirdPartyRepository     thirdPartyRepository;
    private final WarehouseRepository      warehouseRepository;
    private final ProductRepository        productRepository;
    private final StockRepository          stockRepository;
    private final KardexRepository         kardexRepository;
    private final ReceiptRepository        receiptRepository;
    private final PurchaseOrderRepository  purchaseOrderRepository;

    public BatchUseCase(
            BatchRepository batchRepository,
            ThirdPartyRepository thirdPartyRepository,
            WarehouseRepository warehouseRepository,
            ProductRepository productRepository,
            StockRepository stockRepository,
            KardexRepository kardexRepository,
            ReceiptRepository receiptRepository,
            PurchaseOrderRepository purchaseOrderRepository
    ) {
        this.batchRepository        = batchRepository;
        this.thirdPartyRepository   = thirdPartyRepository;
        this.warehouseRepository    = warehouseRepository;
        this.productRepository      = productRepository;
        this.stockRepository        = stockRepository;
        this.kardexRepository       = kardexRepository;
        this.receiptRepository      = receiptRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<BatchResponse> list(Pageable pageable) {
        return PageResponse.from(batchRepository.findAllWithNames(pageable), BatchResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<BatchResponse> listByStatus(BatchStatus status, Pageable pageable) {
        return PageResponse.from(batchRepository.findByStatusWithNames(status, pageable), BatchResponse::from);
    }

    @Transactional(readOnly = true)
    public BatchResponse getById(UUID id) {
        return batchRepository.findByIdWithNames(id)
                .map(BatchResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Lote", id));
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> listChildren(UUID parentId) {
        batchRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Lote padre", parentId));

        return batchRepository.findByParentBatchIdWithNames(parentId).stream()
                .map(BatchResponse::from)
                .toList();
    }

    @Transactional
    public BatchResponse create(BatchRequest request, UUID operatorId) {
        thirdPartyRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Proveedor", request.supplierId()));

        var warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> new ResourceNotFoundException("Bodega", request.warehouseId()));

        if (warehouse.warehouseType() != co.posinvent.domain.model.Warehouse.WarehouseType.CANAL) {
            throw new BusinessException("INVALID_WAREHOUSE_TYPE",
                    "Los lotes de entrada solo se registran en bodegas de tipo CANAL. " +
                    "Bodega seleccionada: " + warehouse.warehouseType());
        }

        var product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto", request.productId()));

        if (request.sourceReceiptId() != null && request.ocId() != null) {
            throw new IllegalArgumentException(
                    "Un lote no puede tener origen en OC y Recepción simultáneamente. " +
                    "Proporcione solo uno.");
        }

        if (request.sourceReceiptId() != null) {
            receiptRepository.findById(request.sourceReceiptId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Recepción", request.sourceReceiptId()));
        }

        if (request.ocId() != null) {
            purchaseOrderRepository.findById(request.ocId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Orden de compra", request.ocId()));
        }

        var batch = new Batch(
                null,
                request.productId(),
                request.supplierId(),
                request.warehouseId(),
                request.entryDate(),
                request.initialWeight(),
                request.purchaseCost(),
                BatchStatus.OPEN,
                request.notes(),
                request.expirationDate(),
                operatorId,
                null,
                null,
                null,
                request.sourceReceiptId(),
                request.ocId(),
                null,
                null,
                null,
                null,
                resolveBatchType(request),
                request.unitOfMeasureId() != null
                        ? request.unitOfMeasureId()
                        : product.unitOfMeasureId()
        );

        var savedBatch = batchRepository.save(batch);

        BigDecimal unitCost = request.initialWeight().compareTo(BigDecimal.ZERO) > 0
                ? request.purchaseCost().divide(request.initialWeight(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        var existingStock = stockRepository.findByProductBatchWarehouse(
                request.productId(), savedBatch.id(), request.warehouseId());

        BigDecimal previousQty = existingStock.map(InventoryStock::currentQuantity).orElse(BigDecimal.ZERO);
        BigDecimal newQty = previousQty.add(request.initialWeight());

        if (existingStock.isPresent()) {
            var s = existingStock.get();
            var avgUnitCost = s.currentQuantity().compareTo(BigDecimal.ZERO) > 0
                    ? s.currentQuantity().multiply(s.unitCost())
                        .add(request.initialWeight().multiply(unitCost))
                        .divide(newQty, 4, RoundingMode.HALF_UP)
                    : unitCost;

            stockRepository.save(new InventoryStock(
                    s.id(), s.productId(), s.batchId(), s.warehouseId(),
                    newQty, s.committedQuantity(), avgUnitCost,
                    s.createdAt(), null));
        } else {
            stockRepository.save(new InventoryStock(
                    null, request.productId(), savedBatch.id(), request.warehouseId(),
                    request.initialWeight(), BigDecimal.ZERO, unitCost,
                    null, null));
        }

        var movement = new InventoryMovement(
                null,
                request.productId(),
                savedBatch.id(),
                request.warehouseId(),
                MovementType.ENTRY,
                request.initialWeight(),
                unitCost,
                previousQty,
                newQty,
                "BATCH_MANUAL",
                savedBatch.id(),
                "Ingreso manual de lote — producto: " + product.name(),
                "SYSTEM",
                null
        );
        kardexRepository.save(movement);

        productRepository.recalculateTotalStock(request.productId());

        return BatchResponse.from(savedBatch);
    }

    @Transactional
    public BatchResponse updateStatus(UUID id, BatchStatus newStatus) {
        var existing = batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lote", id));

        if (existing.status() == BatchStatus.CLOSED) {
            throw new BusinessException("BATCH_IMMUTABLE",
                    "Un lote cerrado no puede modificarse. Usá un Ajuste de Inventario.");
        }

        var updated = new Batch(
                existing.id(), existing.productId(), existing.supplierId(), existing.warehouseId(),
                existing.entryDate(), existing.initialWeight(), existing.purchaseCost(),
                newStatus, existing.notes(), existing.expirationDate(), existing.createdBy(),
                existing.createdAt(), existing.updatedAt(), existing.updatedBy(),
                existing.sourceReceiptId(),
                existing.ocId(),
                existing.productName(),
                existing.supplierName(),
                existing.warehouseName(),
                existing.parentBatchId(),
                existing.batchType(),
                existing.unitOfMeasureId()
        );

        return BatchResponse.from(batchRepository.save(updated));
    }

    private static BatchType resolveBatchType(BatchRequest request) {
        if (request.batchType() != null && !request.batchType().isBlank()) {
            try {
                return BatchType.valueOf(request.batchType().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BusinessException("INVALID_BATCH_TYPE",
                        "Tipo de lote inválido: " + request.batchType()
                        + ". Valores válidos: STANDARD, PARENT, CHILD");
            }
        }
        // Default: PARENT when source document is provided, else STANDARD
        return (request.sourceReceiptId() != null || request.ocId() != null)
                ? BatchType.PARENT
                : BatchType.STANDARD;
    }
}
