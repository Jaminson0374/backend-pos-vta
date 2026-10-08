package co.posinvent.application.usecase;

import co.posinvent.application.dto.TransferResponse;
import co.posinvent.domain.repository.StockTransferRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListTransfersUseCase {

    private final StockTransferRepository transferRepo;

    public ListTransfersUseCase(StockTransferRepository transferRepo) {
        this.transferRepo = transferRepo;
    }

    @Transactional(readOnly = true)
    public Page<TransferResponse> execute(int page, int size) {
        return transferRepo.findAll(PageRequest.of(page, size)).map(TransferResponse::from);
    }
}
