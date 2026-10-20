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
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class CreateTransferUseCase implements CreateTransferPort {

    private final StockTransferRepository transferRepo;
    private final StockRepository stockRepo;
    private final CurrentActorProvider actorProvider;
    private final WarehouseAccessGuard accessGuard;

    public CreateTransferUseCase(
            StockTransferRepository transferRepo,
            StockRepository stockRepo,
            CurrentActorProvider actorProvider,
            WarehouseAccessGuard accessGuard
    ) {
        this.transferRepo = transferRepo;
        this.stockRepo = stockRepo;
        this.actorProvider = actorProvider;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public TransferResponse execute(TransferRequest request) {
        var actor = actorProvider.current();
        var access = accessGuard.memberships(actor);
        accessGuard.requireAny(
                actor,
                access,
                List.of(request.sourceWarehouseId()),
                "CREATE_TRANSFER",
                "source:" + request.sourceWarehouseId()
        );

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
                actor.userId().toString(), null, null, null,
                items
        );

        return TransferResponse.from(transferRepo.save(transfer));
    }
}
