package co.posinvent.application.usecase;

import co.posinvent.application.dto.TransferResponse;
import co.posinvent.application.port.in.GetTransferPort;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.repository.StockTransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetTransferUseCase implements GetTransferPort {

    private final StockTransferRepository transferRepo;

    public GetTransferUseCase(StockTransferRepository transferRepo) {
        this.transferRepo = transferRepo;
    }

    @Transactional(readOnly = true)
    public TransferResponse execute(UUID id) {
        return transferRepo.findById(id)
                .map(TransferResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Traslado", id));
    }
}
