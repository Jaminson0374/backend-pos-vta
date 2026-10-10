package co.posinvent.application.usecase;

import co.posinvent.application.dto.TransferResponse;
import co.posinvent.application.port.in.CancelTransferPort;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.StockTransfer;
import co.posinvent.domain.model.TransferStatus;
import co.posinvent.domain.repository.StockTransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CancelTransferUseCase implements CancelTransferPort {

    private final StockTransferRepository transferRepo;

    public CancelTransferUseCase(StockTransferRepository transferRepo) {
        this.transferRepo = transferRepo;
    }

    @Transactional
    public TransferResponse execute(UUID transferId) {
        var transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Traslado", transferId));

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
