package co.posinvent.application.usecase;

import co.posinvent.application.dto.DisposalResponse;
import co.posinvent.domain.repository.StockDisposalRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListDisposalsUseCase {

    private final StockDisposalRepository disposalRepo;

    public ListDisposalsUseCase(StockDisposalRepository disposalRepo) {
        this.disposalRepo = disposalRepo;
    }

    @Transactional(readOnly = true)
    public Page<DisposalResponse> execute(int page, int size) {
        return disposalRepo.findAll(PageRequest.of(page, size)).map(DisposalResponse::from);
    }
}
