package co.posinvent.application.usecase;

import co.posinvent.application.annotation.Auditable;
import co.posinvent.application.dto.PurchaseReturnRequest;
import co.posinvent.application.dto.PurchaseReturnResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ProcessPurchaseReturnUseCase {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final BatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final RecordMovementUseCase recordMovement;

    public ProcessPurchaseReturnUseCase(
            GoodsReceiptRepository goodsReceiptRepository,
            PurchaseReturnRepository purchaseReturnRepository,
            BatchRepository batchRepository,
            StockRepository stockRepository,
            ProductRepository productRepository,
            RecordMovementUseCase recordMovement
    ) {
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.purchaseReturnRepository = purchaseReturnRepository;
        this.batchRepository = batchRepository;
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.recordMovement = recordMovement;
    }

    @Auditable(entityType = "PURCHASE_RETURN", action = "CREATE")
    @Transactional
    public PurchaseReturnResponse execute(PurchaseReturnRequest request, UUID operatorId) {
        // 1. Load receipt: must exist with status=COMPLETED
        var receipt = goodsReceiptRepository.findById(request.receiptId())
                .orElseThrow(() -> new ResourceNotFoundException("Remisión", request.receiptId()));

        if (receipt.status() != GoodsReceiptStatus.COMPLETED
                && receipt.status() != GoodsReceiptStatus.HIGH_COST_DEVIATION) {
            throw new BusinessException("RECEIPT_NOT_PROCESSABLE",
                    "La remisión no está en estado COMPLETED. Estado actual: " + receipt.status());
        }

        // 2. Check no prior return for this receipt
        var existingReturn = purchaseReturnRepository.findByReceiptId(request.receiptId());
        if (existingReturn.isPresent()) {
            throw new BusinessException("DUPLICATE_RETURN",
                    "Ya existe una devolución para esta remisión");
        }

        // Build map: productId -> receipt line for quick lookup
        var receiptLineByProduct = receipt.lines().stream()
                .collect(Collectors.toMap(ReceiptLineItem::productId, line -> line));

        // 3. Validate items
        for (var item : request.items()) {
            var rl = receiptLineByProduct.get(item.productId());
            if (rl == null) {
                throw new BusinessException("PRODUCT_NOT_IN_RECEIPT",
                        "El producto " + item.productId() + " no está en la remisión");
            }
            if (item.returnQty().compareTo(rl.receivedQty()) > 0) {
                throw new BusinessException("QTY_EXCEEDS_RECEIVED",
                        "La cantidad a devolver (" + item.returnQty()
                        + ") excede lo recibido (" + rl.receivedQty()
                        + ") para el producto " + item.productId());
            }
            if (item.batchId() != null) {
                batchRepository.findById(item.batchId())
                        .orElseThrow(() -> new ResourceNotFoundException("Lote", item.batchId()));
            }
        }

        // 4. Calculate total
        var totalReturned = request.items().stream()
                .map(item -> item.returnQty().multiply(item.unitCost()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Create PurchaseReturn
        var returnId = UUID.randomUUID();
        var returnDate = LocalDate.now();
        var documentNumber = generateDocumentNumber();

        var returnLines = new ArrayList<PurchaseReturnLine>();
        int lineNum = 0;
        for (var item : request.items()) {
            lineNum++;
            returnLines.add(new PurchaseReturnLine(
                    null,
                    returnId,
                    item.productId(),
                    item.warehouseId(),
                    item.batchId(),
                    item.returnQty(),
                    item.unitCost(),
                    lineNum
            ));
        }

        var purchaseReturn = new PurchaseReturn(
                returnId,
                request.receiptId(),
                returnDate,
                documentNumber,
                request.reason(),
                PurchaseReturnStatus.COMPLETED,
                operatorId,
                null,
                null,
                null,
                returnLines
        );
        var saved = purchaseReturnRepository.save(purchaseReturn);

        // 6. Reverse stock per line
        var affectedBatches = new HashSet<UUID>();
        for (var returnLine : returnLines) {
            if (returnLine.batchId() != null) {
                // 6a. Update batch quantity
                var batch = batchRepository.findById(returnLine.batchId())
                        .orElseThrow(() -> new ResourceNotFoundException("Lote", returnLine.batchId()));
                affectedBatches.add(batch.id());

                var newBatchQty = batch.initialWeight().subtract(returnLine.returnQty());
                var batchStatus = newBatchQty.compareTo(BigDecimal.ZERO) <= 0
                        ? BatchStatus.CLOSED
                        : BatchStatus.OPEN;

                batchRepository.save(new Batch(
                        batch.id(), batch.productId(), batch.supplierId(), batch.warehouseId(),
                        batch.entryDate(), newBatchQty, batch.purchaseCost(),
                        batchStatus, batch.notes(), batch.expirationDate(),
                        batch.createdBy(), batch.createdAt(), null, batch.updatedBy(),
                        batch.sourceReceiptId(), batch.ocId(),
                        null, null, null,
                        batch.parentBatchId(), batch.batchType(), batch.unitOfMeasureId()
                ));
            }

            // 6b. Update InventoryStock
            var stockOpt = stockRepository.findByProductBatchWarehouse(
                    returnLine.productId(), returnLine.batchId(), returnLine.warehouseId());
            var previousQty = BigDecimal.ZERO;
            if (stockOpt.isPresent()) {
                var stock = stockOpt.get();
                previousQty = stock.currentQuantity();
                var newQty = stock.currentQuantity().subtract(returnLine.returnQty());
                if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                    newQty = BigDecimal.ZERO;
                }
                stockRepository.save(new InventoryStock(
                        stock.id(), stock.productId(), stock.batchId(),
                        stock.warehouseId(), newQty, stock.committedQuantity(),
                        stock.unitCost(), stock.createdAt(), null
                ));
            }

            // 6c. Record movement
            recordMovement.record(
                    returnLine.productId(),
                    returnLine.batchId(),
                    returnLine.warehouseId(),
                    MovementType.EXIT,
                    returnLine.returnQty(),
                    returnLine.unitCost(),
                    previousQty,
                    previousQty.subtract(returnLine.returnQty()),
                    "PURCHASE_RETURN",
                    returnId,
                    request.reason() != null ? request.reason() : "Devolución por remisión"
            );

            // 6d. Recalculate total stock
            productRepository.recalculateTotalStock(returnLine.productId());
        }

        // 7. Build response
        return PurchaseReturnResponse.from(saved, affectedBatches.size(), totalReturned);
    }

    private String generateDocumentNumber() {
        var today = LocalDate.now();
        var prefix = "DR-" + today.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        var last = purchaseReturnRepository.findFirstByDocumentNumberStartingWith(prefix);
        var seq = last.map(pr -> {
            var parts = pr.documentNumber().split("-");
            return Integer.parseInt(parts[parts.length - 1]) + 1;
        }).orElse(1);
        return prefix + String.format("%04d", seq);
    }
}
