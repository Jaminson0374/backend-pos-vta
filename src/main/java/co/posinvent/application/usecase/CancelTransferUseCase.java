package co.posinvent.application.usecase;

import co.posinvent.application.dto.TransferResponse;
import co.posinvent.application.port.in.CancelTransferPort;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.StockTransfer;
import co.posinvent.domain.model.TransferStatus;
import co.posinvent.domain.repository.StockTransferRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CancelTransferUseCase implements CancelTransferPort {

    private final StockTransferRepository transferRepo;
    private final CurrentActorProvider actorProvider;
    private final WarehouseAccessGuard accessGuard;

    public CancelTransferUseCase(
            StockTransferRepository transferRepo,
            CurrentActorProvider actorProvider,
            WarehouseAccessGuard accessGuard
    ) {
        this.transferRepo = transferRepo;
        this.actorProvider = actorProvider;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public TransferResponse execute(UUID transferId) {
        var transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Traslado", transferId));

        var actor = actorProvider.current();
        var access = accessGuard.memberships(actor);
        accessGuard.requireAny(
                actor,
                access,
                List.of(transfer.sourceWarehouseId()),
                "CANCEL_TRANSFER",
                transferId
        );

        if (transfer.status() != TransferStatus.DRAFT) {
            throw new BusinessException("TRANSFER_NOT_DRAFT", "Solo traslados en DRAFT pueden cancelarse.");
        }

        var cancelled = new StockTransfer(
                transfer.id(), transfer.sourceWarehouseId(), transfer.targetWarehouseId(),
                TransferStatus.CANCELLED, transfer.notes(),
                transfer.createdBy(), transfer.createdAt(), transfer.confirmedBy(), transfer.confirmedAt(),
                transfer.items()
        );

        return TransferResponse.from(transferRepo.save(cancelled));
    }
}
