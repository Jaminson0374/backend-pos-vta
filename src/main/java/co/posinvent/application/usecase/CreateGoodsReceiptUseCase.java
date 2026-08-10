package co.posinvent.application.usecase;

import co.posinvent.application.dto.GoodsReceiptRequest;
import co.posinvent.application.dto.GoodsReceiptResponse;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.repository.*;
import co.posinvent.domain.service.ReceiptDomainService;
import co.posinvent.domain.service.ReceiptDomainService.ReceiptLineItemInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CreateGoodsReceiptUseCase {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final BatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final RecordMovementUseCase recordMovement;
    private final ReceiptDomainService domainService;

    public CreateGoodsReceiptUseCase(
            PurchaseOrderRepository purchaseOrderRepository,
            GoodsReceiptRepository goodsReceiptRepository,
            BatchRepository batchRepository,
            StockRepository stockRepository,
            ProductRepository productRepository,
            RecordMovementUseCase recordMovement,
            ReceiptDomainService domainService
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.batchRepository = batchRepository;
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.recordMovement = recordMovement;
        this.domainService = domainService;
    }

    @Transactional
    public GoodsReceiptResponse process(GoodsReceiptRequest request, UUID operatorId) {
        // 1. Fetch PurchaseOrder (must exist)
        var oc = purchaseOrderRepository.findById(request.ocId())
                .orElseThrow(() -> new ResourceNotFoundException("Orden de compra", request.ocId()));

        // 2. Validate OC is PENDING or PARTIAL
        domainService.validateOcProcessable(oc);

        // Convert request lines to domain input
        var receiptInputs = request.lines().stream()
                .map(line -> new ReceiptLineItemInput(
                        line.productId(), line.warehouseId(),
                        line.receivedQty(), line.actualCost(),
                        line.expirationDate()))
                .toList();

        // 3. Validate lines against OC
        domainService.validateLines(oc, receiptInputs);

        // 4. Compute cost deviations (warning, never blocks)
        var deviations = domainService.computeDeviations(oc, receiptInputs);
        var hasHighDeviation = !deviations.isEmpty();

        // 5. Generate receipt ID and save GoodsReceipt FIRST
        // (so FK on batches.source_receipt_id is valid when batches are created)
        var receiptId = UUID.randomUUID();
        var receiptDate = LocalDate.now();
        var batchIds = new ArrayList<UUID>();
        var expirationDates = new ArrayList<LocalDate>();
        var receiptLineItems = new ArrayList<ReceiptLineItem>();

        // Build receipt line items from inputs
        for (var input : receiptInputs) {
            receiptLineItems.add(new ReceiptLineItem(
                    null,
                    receiptId,
                    input.productId(),
                    input.warehouseId(),
                    input.receivedQty(),
                    input.actualCost()
            ));
        }

        // 5a. Save GoodsReceipt and flush immediately so FK on batches.source_receipt_id is valid
        var goodsReceipt = new GoodsReceipt(
                receiptId,
                oc.id(),
                receiptDate,
                hasHighDeviation ? GoodsReceiptStatus.HIGH_COST_DEVIATION : GoodsReceiptStatus.COMPLETED,
                "Recepción #" + receiptId + " — OC #" + oc.id(),
                operatorId,
                null,
                null,
                null,
                receiptLineItems,
                batchIds
        );
        goodsReceiptRepository.saveAndFlush(goodsReceipt);

        // Build map of OC lines by productId for quick lookup
        var ocLineByProduct = oc.lines().stream()
                .collect(Collectors.toMap(PurchaseLineItem::productId, line -> line));

        // 6. For each receipt line: create batch, upsert stock
        for (var input : receiptInputs) {
            var ocLine = ocLineByProduct.get(input.productId());

            // 6a. Create Batch with sourceReceiptId (FK now valid)
            var batch = batchRepository.save(new Batch(
                    null,
                    input.productId(),
                    oc.supplierId(),
                    input.warehouseId(),
                    receiptDate,
                    input.receivedQty(),
                    input.actualCost(),
                    BatchStatus.OPEN,
                    "Recepción #" + receiptId + " vs OC #" + oc.id(),
                    input.expirationDate(),
                    operatorId,
                    null,
                    null,
                    null,
                    receiptId,
                    oc.id(),
                    null,
                    null,
                    null,
                    null,
                    BatchType.PARENT,
                    null
            ));
            batchIds.add(batch.id());
            expirationDates.add(input.expirationDate());

            // 6b. Upsert stock: find existing or create new stock entry
            var existingStock = stockRepository.findByProductBatchWarehouse(
                    input.productId(), batch.id(), input.warehouseId());

            var unitCost = input.actualCost()
                    .divide(input.receivedQty(), 6, RoundingMode.HALF_UP);

            var previousQty = BigDecimal.ZERO;
            if (existingStock.isPresent()) {
                var stock = existingStock.get();
                previousQty = stock.currentQuantity();
                stockRepository.save(new InventoryStock(
                        stock.id(),
                        stock.productId(),
                        stock.batchId(),
                        stock.warehouseId(),
                        input.receivedQty(),
                        BigDecimal.ZERO,
                        unitCost,
                        stock.createdAt(),
                        null
                ));
            } else {
                stockRepository.save(new InventoryStock(
                        null,
                        input.productId(),
                        batch.id(),
                        input.warehouseId(),
                        input.receivedQty(),
                        BigDecimal.ZERO,
                        unitCost,
                        null,
                        null
                ));
            }
            productRepository.recalculateTotalStock(input.productId());
            recordMovement.record(
                    input.productId(), batch.id(), input.warehouseId(),
                    co.posinvent.domain.model.MovementType.ENTRY,
                    input.receivedQty(), unitCost,
                    previousQty, input.receivedQty(),
                    "GOODS_RECEIPT", receiptId,
                    "Recepción #" + receiptId
            );
        }

        // 7. Update OC line receivedQty
        var updatedOcLines = oc.lines().stream()
                .map(ocLine -> {
                    var matchingInput = receiptInputs.stream()
                            .filter(r -> r.productId().equals(ocLine.productId()))
                            .findFirst();
                    if (matchingInput.isPresent()) {
                        var newReceived = ocLine.receivedQty().add(matchingInput.get().receivedQty());
                        return new PurchaseLineItem(
                                ocLine.id(), ocLine.ocId(), ocLine.productId(),
                                ocLine.warehouseId(), ocLine.orderedQty(),
                                newReceived, ocLine.unitCost(),
                                ocLine.discountPct(), ocLine.taxType(),
                                ocLine.lineNumber()
                        );
                    }
                    return ocLine;
                })
                .toList();

        // 8. Update PurchaseOrder status
        var allReceived = updatedOcLines.stream()
                .allMatch(line -> line.receivedQty().compareTo(line.orderedQty()) >= 0);
        var newOcStatus = allReceived
                ? PurchaseOrderStatus.RECEIVED
                : PurchaseOrderStatus.PARTIAL;

        purchaseOrderRepository.save(new PurchaseOrder(
                oc.id(),
                oc.supplierId(),
                newOcStatus,
                oc.orderDate(),
                oc.documentNumber(),
                oc.notes(),
                oc.dueDate(),
                oc.buyerId(),
                oc.paymentMethod(),
                oc.supportDocumentType(),
                oc.supportDocumentNumber(),
                oc.currency(),
                oc.createdBy(),
                oc.createdAt(),
                OffsetDateTime.now(),
                oc.version(),
                updatedOcLines
        ));

        // 9. Return response (batchIds collected during loop, deviations from domain service)
        return GoodsReceiptResponse.from(goodsReceipt, batchIds, expirationDates, deviations);
    }
}
