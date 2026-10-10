package co.posinvent.application.usecase;

import co.posinvent.application.dto.AdjustmentResponse;
import co.posinvent.application.port.in.ListAdjustmentsPort;
import co.posinvent.domain.model.AdjustmentType;
import co.posinvent.domain.repository.StockAdjustmentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class ListAdjustmentsUseCase implements ListAdjustmentsPort {

    private final StockAdjustmentRepository adjustmentRepo;

    public ListAdjustmentsUseCase(StockAdjustmentRepository adjustmentRepo) {
        this.adjustmentRepo = adjustmentRepo;
    }

    public Page<AdjustmentResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return adjustmentRepo.findAll(pageable).map(AdjustmentResponse::from);
    }

    public Page<AdjustmentResponse> findByProduct(UUID productId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return adjustmentRepo.findByProductId(productId, pageable).map(AdjustmentResponse::from);
    }

    public Page<AdjustmentResponse> listFiltered(
            UUID productId, UUID warehouseId, String adjustmentType,
            OffsetDateTime from, OffsetDateTime to, Pageable pageable
    ) {
        if (productId != null) {
            return adjustmentRepo.findByProductId(productId, pageable).map(AdjustmentResponse::from);
        }
        return adjustmentRepo.findAll(pageable).map(AdjustmentResponse::from);
    }
}
