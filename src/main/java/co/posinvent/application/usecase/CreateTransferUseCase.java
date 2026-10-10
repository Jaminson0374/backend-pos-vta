package co.posinvent.application.usecase;

import co.posinvent.application.dto.TransferRequest;
import co.posinvent.application.dto.TransferResponse;
import co.posinvent.application.port.in.CreateTransferPort;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.model.StockTransfer;
import co.posinvent.domain.model.StockTransferItem;
import co.posinvent.domain.model.TransferStatus;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.StockTransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;

@Service
public class CreateTransferUseCase implements CreateTransferPort {

    private final StockTransferRepository transferRepo;
    private final StockRepository stockRepo;

    public CreateTransferUseCase(StockTransferRepository transferRepo, StockRepository stockRepo) {
        this.transferRepo = transferRepo;
        this.stockRepo = stockRepo;
    }

    @Transactional
    public TransferResponse execute(TransferRequest request) {
        var items = new ArrayList<StockTransferItem>();
        for (var item : request.items()) {
            var sourceStock = stockRepo.findByProductBatchWarehouse(
                    item.productId(), item.batchId(), request.sourceWarehouseId());
            if (sourceStock.isEmpty() || sourceStock.get().currentQuantity().compareTo(item.quantity()) < 0) {
                throw new BusinessException("TRANSFER_NO_STOCK",
                        "Stock insuficiente en bodega origen para producto " + item.productId());
            }
            items.add(new StockTransferItem(
                    null, null, item.productId(), item.batchId(),
                    item.quantity(), BigDecimal.ZERO
            ));
        }

        var transfer = new StockTransfer(
                null,
                request.sourceWarehouseId(),
                request.targetWarehouseId(),
                TransferStatus.DRAFT,
                request.notes(),
                "SYSTEM", null, null, null,
                items
        );

        return TransferResponse.from(transferRepo.save(transfer));
    }
}
