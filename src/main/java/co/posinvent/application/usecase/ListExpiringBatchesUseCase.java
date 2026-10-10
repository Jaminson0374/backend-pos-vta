package co.posinvent.application.usecase;

import co.posinvent.domain.repository.WasteDisposalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class ListExpiringBatchesUseCase {

    private final WasteDisposalRepository disposalRepo;

    public ListExpiringBatchesUseCase(WasteDisposalRepository disposalRepo) {
        this.disposalRepo = disposalRepo;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(int days) {
        return disposalRepo.findExpiringBatches(days);
    }
}
