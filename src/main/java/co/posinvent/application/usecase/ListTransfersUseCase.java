package co.posinvent.application.usecase;

import co.posinvent.application.dto.TransferResponse;
import co.posinvent.application.port.in.ListTransfersPort;
import co.posinvent.domain.repository.StockTransferRepository;
import co.posinvent.infrastructure.adapters.out.security.CurrentActorProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListTransfersUseCase implements ListTransfersPort {

    private final StockTransferRepository transferRepo;
    private final CurrentActorProvider actorProvider;

    public ListTransfersUseCase(
            StockTransferRepository transferRepo,
            CurrentActorProvider actorProvider
    ) {
        this.transferRepo = transferRepo;
        this.actorProvider = actorProvider;
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> execute(int page, int size) {
        var actor = actorProvider.current();
        boolean restricted = !actor.isAdmin();
        return transferRepo.findVisible(actor.userId(), restricted, PageRequest.of(page, size))
                .map(TransferResponse::from);
    }
}
