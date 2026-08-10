package co.posinvent.application.usecase;

import co.posinvent.application.dto.*;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.repository.*;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.UUID;

@Service
public class LogisticsUseCase {

    private final ReceiptRepository receiptRepo;
    private final PickingRepository pickingRepo;
    private final ShipmentRepository shipmentRepo;
    private final TransportGuideRepository guideRepo;
    private final BatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final RecordMovementUseCase recordMovement;
    private final ProductRepository productRepository;

    public LogisticsUseCase(ReceiptRepository receiptRepo,
                            PickingRepository pickingRepo,
                            ShipmentRepository shipmentRepo,
                            TransportGuideRepository guideRepo,
                            BatchRepository batchRepository,
                            StockRepository stockRepository,
                            RecordMovementUseCase recordMovement,
                            ProductRepository productRepository) {
        this.receiptRepo = receiptRepo;
        this.pickingRepo = pickingRepo;
        this.shipmentRepo = shipmentRepo;
        this.guideRepo = guideRepo;
        this.batchRepository = batchRepository;
        this.stockRepository = stockRepository;
        this.recordMovement = recordMovement;
        this.productRepository = productRepository;
    }

    // ── Receipts ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<ReceiptResponse> listReceipts(Pageable pageable) {
        return PageResponse.from(receiptRepo.findAll(pageable), ReceiptResponse::from);
    }

    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(UUID id) {
        return receiptRepo.findById(id)
                .map(ReceiptResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt", id));
    }

    @Transactional
    public ReceiptResponse createReceipt(ReceiptRequest request) {
        // 1. Map request items to domain items (batchId initially null — assigned after batch creation)
        var items = request.items().stream()
                .map(i -> new Receipt.ReceiptItem(
                        i.id(), null, i.productId(), i.warehouseId(), null,
                        i.orderedQuantity(), i.receivedQuantity(), i.unitCost(), i.notes()))
                .toList();

        // 2. Create and save the receipt first so we have a valid FK target for batches
        var receipt = new Receipt(
                null, request.receiptNumber(), request.receiptDate(), request.supplierId(),
                request.purchaseOrderId(), request.warehouseId(), ReceiptStatus.PENDING,
                request.notes(), null, null, null, null, items);

        var savedReceipt = receiptRepo.save(receipt);

        // 3. For each line item: create Batch → upsert InventoryStock → record Kardex → recalculate
        var updatedItems = new ArrayList<Receipt.ReceiptItem>();

        for (var item : savedReceipt.items()) {
            var quantity = item.receivedQuantity();
            var unitCost = item.unitCost();
            var totalCost = quantity.multiply(unitCost);
            var entryDate = savedReceipt.receiptDate();

            // 3a. Create Batch — sourceReceiptId provides traceability back to this receipt
            var batch = new Batch(
                    null,
                    item.productId(),
                    savedReceipt.supplierId(),
                    item.warehouseId(),
                    entryDate,
                    quantity,
                    totalCost,
                    BatchStatus.OPEN,
                    "Recepción #" + savedReceipt.id(),
                    null,       // expirationDate
                    null,       // createdBy
                    null,       // createdAt
                    null,       // updatedAt
                    null,       // updatedBy
                    savedReceipt.id(),  // sourceReceiptId — traceability
                    null,       // ocId — logistics receipts don't require an OC
                    null,       // productName (display)
                    null,       // supplierName (display)
                    null,       // warehouseName (display)
                    null,       // parentBatchId
                    BatchType.PARENT,   // traceable via sourceReceiptId
                    null        // unitOfMeasureId
            );
            var savedBatch = batchRepository.save(batch);

            // 3b. Upsert InventoryStock — find existing or create new with weighted avg cost
            var existingStock = stockRepository.findByProductBatchWarehouse(
                    item.productId(), savedBatch.id(), item.warehouseId());

            var previousQty = BigDecimal.ZERO;
            if (existingStock.isPresent()) {
                var s = existingStock.get();
                previousQty = s.currentQuantity();
                var newQty = s.currentQuantity().add(quantity);
                var avgUnitCost = s.currentQuantity().compareTo(BigDecimal.ZERO) > 0
                        ? s.currentQuantity().multiply(s.unitCost())
                            .add(quantity.multiply(unitCost))
                            .divide(newQty, 4, RoundingMode.HALF_UP)
                        : unitCost;

                stockRepository.save(new InventoryStock(
                        s.id(), s.productId(), s.batchId(), s.warehouseId(),
                        newQty, s.committedQuantity(), avgUnitCost,
                        s.createdAt(), null));
            } else {
                stockRepository.save(new InventoryStock(
                        null, item.productId(), savedBatch.id(), item.warehouseId(),
                        quantity, BigDecimal.ZERO, unitCost,
                        null, null));
            }

            // 3c. Record Kardex — ENTRY type, referenceType="ENTRY_LOGISTICS"
            recordMovement.record(
                    item.productId(), savedBatch.id(), item.warehouseId(),
                    MovementType.ENTRY,
                    quantity, unitCost,
                    previousQty, previousQty.add(quantity),
                    "ENTRY_LOGISTICS", savedReceipt.id(),
                    "Recepción #" + savedReceipt.id()
            );

            // 3d. Recalculate product total stock
            productRepository.recalculateTotalStock(item.productId());

            // Update item with the newly created batchId
            updatedItems.add(new Receipt.ReceiptItem(
                    item.id(), item.receiptId(), item.productId(), item.warehouseId(),
                    savedBatch.id(), item.orderedQuantity(), item.receivedQuantity(),
                    item.unitCost(), item.notes()));
        }

        // 4. Update receipt items with batchIds and return
        var finalReceipt = new Receipt(
                savedReceipt.id(), savedReceipt.receiptNumber(), savedReceipt.receiptDate(),
                savedReceipt.supplierId(), savedReceipt.purchaseOrderId(), savedReceipt.warehouseId(),
                savedReceipt.status(), savedReceipt.notes(), savedReceipt.createdBy(),
                savedReceipt.createdAt(), savedReceipt.updatedAt(), savedReceipt.version(),
                updatedItems);

        return ReceiptResponse.from(receiptRepo.save(finalReceipt));
    }

    @Transactional
    public ReceiptResponse updateReceiptStatus(UUID id, String status) {
        var existing = receiptRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Receipt", id));

        var newStatus = ReceiptStatus.valueOf(status.toUpperCase());
        var updated = new Receipt(
                existing.id(), existing.receiptNumber(), existing.receiptDate(),
                existing.supplierId(), existing.purchaseOrderId(), existing.warehouseId(),
                newStatus, existing.notes(), existing.createdBy(),
                existing.createdAt(), existing.updatedAt(), existing.version(), existing.items());

        return ReceiptResponse.from(receiptRepo.save(updated));
    }

    // ── Pickings ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<PickingResponse> listPickings(Pageable pageable) {
        return PageResponse.from(pickingRepo.findAll(pageable), PickingResponse::from);
    }

    @Transactional(readOnly = true)
    public PickingResponse getPicking(UUID id) {
        return pickingRepo.findById(id)
                .map(PickingResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Picking", id));
    }

    @Transactional
    public PickingResponse createPicking(PickingRequest request) {
        var items = request.items().stream()
                .map(i -> new Picking.PickingItem(
                        i.id(), null, i.productId(), i.warehouseId(), i.locationId(),
                        i.batchId(), i.requestedQuantity(),
                        i.pickedQuantity() != null ? i.pickedQuantity() : java.math.BigDecimal.ZERO,
                        i.notes()))
                .toList();

        var picking = new Picking(
                null, request.pickingNumber(), request.pickingDate(), request.warehouseId(),
                request.shipmentId(), request.salesOrderId(), PickingStatus.PLANNED,
                request.notes(), null, null, null, null, items);

        return PickingResponse.from(pickingRepo.save(picking));
    }

    @Transactional
    public PickingResponse updatePickingStatus(UUID id, String status) {
        var existing = pickingRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Picking", id));

        var newStatus = PickingStatus.valueOf(status.toUpperCase());
        var updated = new Picking(
                existing.id(), existing.pickingNumber(), existing.pickingDate(),
                existing.warehouseId(), existing.shipmentId(), existing.salesOrderId(),
                newStatus, existing.notes(), existing.createdBy(),
                existing.createdAt(), existing.updatedAt(), existing.version(), existing.items());

        return PickingResponse.from(pickingRepo.save(updated));
    }

    // ── Shipments ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<ShipmentResponse> listShipments(Pageable pageable) {
        return PageResponse.from(shipmentRepo.findAll(pageable), ShipmentResponse::from);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getShipment(UUID id) {
        return shipmentRepo.findById(id)
                .map(ShipmentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", id));
    }

    @Transactional
    public ShipmentResponse createShipment(ShipmentRequest request) {
        var items = request.items().stream()
                .map(i -> new Shipment.ShipmentItem(
                        i.id(), null, i.productId(), i.pickingId(),
                        i.batchId(), i.quantity(), i.notes()))
                .toList();

        var shipment = new Shipment(
                null, request.shipmentNumber(), request.shipmentDate(),
                request.carrierName(), request.vehiclePlate(), request.driverName(),
                request.transportGuideId(), ShipmentStatus.DRAFT,
                request.notes(), null, null, null, null, items);

        return ShipmentResponse.from(shipmentRepo.save(shipment));
    }

    @Transactional
    public ShipmentResponse updateShipmentStatus(UUID id, String status) {
        var existing = shipmentRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment", id));

        var newStatus = ShipmentStatus.valueOf(status.toUpperCase());
        var updated = new Shipment(
                existing.id(), existing.shipmentNumber(), existing.shipmentDate(),
                existing.carrierName(), existing.vehiclePlate(), existing.driverName(),
                existing.transportGuideId(), newStatus, existing.notes(),
                existing.createdBy(), existing.createdAt(), existing.updatedAt(),
                existing.version(), existing.items());

        return ShipmentResponse.from(shipmentRepo.save(updated));
    }

    // ── Transport Guides ──────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<TransportGuideResponse> listGuides(Pageable pageable) {
        return PageResponse.from(guideRepo.findAll(pageable), TransportGuideResponse::from);
    }

    @Transactional(readOnly = true)
    public TransportGuideResponse getGuide(UUID id) {
        return guideRepo.findById(id)
                .map(TransportGuideResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("TransportGuide", id));
    }

    @Transactional
    public TransportGuideResponse createGuide(TransportGuideRequest request) {
        var guide = new TransportGuide(
                null, request.guideNumber(), request.issueDate(), request.vehiclePlate(),
                request.driverName(), request.driverId(), request.originAddress(),
                request.destinationAddress(), request.carrierName(),
                request.estimatedDelivery(), TransportGuideStatus.CREATED,
                request.notes(), null, null, null, null,
                request.shipmentIds() != null ? request.shipmentIds() : java.util.List.of());

        return TransportGuideResponse.from(guideRepo.save(guide));
    }

    @Transactional
    public TransportGuideResponse updateGuideStatus(UUID id, String status) {
        var existing = guideRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TransportGuide", id));

        var newStatus = TransportGuideStatus.valueOf(status.toUpperCase());
        var updated = new TransportGuide(
                existing.id(), existing.guideNumber(), existing.issueDate(),
                existing.vehiclePlate(), existing.driverName(), existing.driverId(),
                existing.originAddress(), existing.destinationAddress(),
                existing.carrierName(), existing.estimatedDelivery(),
                newStatus, existing.notes(), existing.createdBy(),
                existing.createdAt(), existing.updatedAt(), existing.version(),
                existing.shipmentIds());

        return TransportGuideResponse.from(guideRepo.save(updated));
    }
}
