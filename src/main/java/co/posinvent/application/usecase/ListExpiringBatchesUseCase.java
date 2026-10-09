package co.posinvent.application.usecase;

import co.posinvent.application.port.in.ListExpiringBatchesPort;
import co.posinvent.domain.repository.StockDisposalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ListExpiringBatchesUseCase implements ListExpiringBatchesPort {

    private final StockDisposalRepository disposalRepo;

    public ListExpiringBatchesUseCase(StockDisposalRepository disposalRepo) {
        this.disposalRepo = disposalRepo;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(int days) {
        return disposalRepo.findExpiringBatches(days);
    }
}
