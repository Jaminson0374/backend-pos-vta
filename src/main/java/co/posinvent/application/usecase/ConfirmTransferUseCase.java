package co.posinvent.application.usecase;

import co.posinvent.application.dto.TransferResponse;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.*;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.StockTransferRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Service
public class ConfirmTransferUseCase {

    private final StockTransferRepository transferRepo;
    private final StockRepository stockRepo;
    private final ProductRepository productRepo;
    private final RecordMovementUseCase recordMovement;
    private final OptimisticConcurrencyExecutor concurrencyExecutor;

    public ConfirmTransferUseCase(
            StockTransferRepository transferRepo, StockRepository stockRepo,
            ProductRepository productRepo, RecordMovementUseCase recordMovement,
            OptimisticConcurrencyExecutor concurrencyExecutor
    ) {
        this.transferRepo = transferRepo;
        this.stockRepo = stockRepo;
        this.productRepo = productRepo;
        this.recordMovement = recordMovement;
        this.concurrencyExecutor = concurrencyExecutor;
    }

    public TransferResponse execute(java.util.UUID transferId) {
        return concurrencyExecutor.execute(() -> {
            var transfer = transferRepo.findById(transferId)
                    .orElseThrow(() -> new ResourceNotFoundException("Traslado", transferId));

            if (transfer.status() != TransferStatus.DRAFT) {
                throw new BusinessException("TRANSFER_NOT_DRAFT", "Solo traslados en DRAFT pueden confirmarse.");
            }

            for (var item : transfer.items()) {
                // Decrement source warehouse
                var sourceStock = stockRepo.findByProductBatchWarehouse(
                        item.productId(), item.batchId(), transfer.sourceWarehouseId());
                if (sourceStock.isEmpty() || sourceStock.get().currentQuantity().compareTo(item.quantity()) < 0) {
                    throw new BusinessException("TRANSFER_NO_STOCK",
                            "Stock insuficiente en origen para producto " + item.productId());
                }

                var src = sourceStock.get();
                var srcNew = src.currentQuantity().subtract(item.quantity());
                stockRepo.save(new InventoryStock(
                        src.id(), src.productId(), src.batchId(), src.warehouseId(),
                        srcNew, src.committedQuantity(), src.unitCost(), src.createdAt(), src.updatedAt()
                ));
                recordMovement.record(item.productId(), item.batchId(), transfer.sourceWarehouseId(),
                        MovementType.TRANSFER_OUT, item.quantity(), src.unitCost(),
                        src.currentQuantity(), srcNew, "TRANSFER", transferId, "Traslado salida");

                // Increment target warehouse
                var targetStock = stockRepo.findByProductBatchWarehouse(
                        item.productId(), item.batchId(), transfer.targetWarehouseId());
                BigDecimal prevTgt = BigDecimal.ZERO;
                if (targetStock.isPresent()) {
                    var tgt = targetStock.get();
                    prevTgt = tgt.currentQuantity();
                    var tgtNew = tgt.currentQuantity().add(item.quantity());
                    stockRepo.save(new InventoryStock(
                            tgt.id(), tgt.productId(), tgt.batchId(), tgt.warehouseId(),
                            tgtNew, tgt.committedQuantity(), tgt.unitCost(), tgt.createdAt(), tgt.updatedAt()
                    ));
                } else {
                    stockRepo.save(new InventoryStock(
                            null, item.productId(), item.batchId(), transfer.targetWarehouseId(),
                            item.quantity(), BigDecimal.ZERO, src.unitCost(), null, null
                    ));
                }
                recordMovement.record(item.productId(), item.batchId(), transfer.targetWarehouseId(),
                        MovementType.TRANSFER_IN, item.quantity(), src.unitCost(),
                        prevTgt, prevTgt.add(item.quantity()), "TRANSFER", transferId, "Traslado entrada");

                productRepo.recalculateTotalStock(item.productId());
            }

            var confirmed = new StockTransfer(
                    transfer.id(), transfer.sourceWarehouseId(), transfer.targetWarehouseId(),
                    TransferStatus.CONFIRMED, transfer.notes(),
                    transfer.createdBy(), transfer.createdAt(), "SYSTEM", OffsetDateTime.now(),
                    transfer.items()
            );

            return TransferResponse.from(transferRepo.save(confirmed));
        });
    }
}
