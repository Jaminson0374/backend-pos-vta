package co.posinvent.application.usecase;

import co.posinvent.application.dto.TransferResponse;
import co.posinvent.application.port.in.GetTransferPort;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.repository.StockTransferRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class GetTransferUseCase implements GetTransferPort {

    private final StockTransferRepository transferRepo;
    private final CurrentActorProvider actorProvider;
    private final WarehouseAccessGuard accessGuard;

    public GetTransferUseCase(
            StockTransferRepository transferRepo,
            CurrentActorProvider actorProvider,
            WarehouseAccessGuard accessGuard
    ) {
        this.transferRepo = transferRepo;
        this.actorProvider = actorProvider;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public TransferResponse execute(UUID id) {
        var actor = actorProvider.current();
        var transfer = transferRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Traslado", id));

        var access = accessGuard.memberships(actor);
        accessGuard.requireAny(
                actor,
                access,
                List.of(transfer.sourceWarehouseId(), transfer.targetWarehouseId()),
                "READ_TRANSFER",
                transfer.id()
        );

        return TransferResponse.from(transfer);
    }
}
