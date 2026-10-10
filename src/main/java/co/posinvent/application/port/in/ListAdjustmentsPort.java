package co.posinvent.application.port.in;

import co.posinvent.application.dto.AdjustmentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ListAdjustmentsPort {

    Page<AdjustmentResponse> listFiltered(
            UUID productId, UUID warehouseId, String adjustmentType,
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}
