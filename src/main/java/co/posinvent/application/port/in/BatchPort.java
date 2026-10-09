package co.posinvent.application.port.in;

import co.posinvent.application.dto.BatchRequest;
import co.posinvent.application.dto.BatchResponse;
import co.posinvent.application.dto.PageResponse;
import co.posinvent.domain.model.Batch.BatchStatus;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface BatchPort {

    PageResponse<BatchResponse> list(Pageable pageable);

    PageResponse<BatchResponse> listByStatus(BatchStatus status, Pageable pageable);

    BatchResponse getById(UUID id);

    BatchResponse create(BatchRequest request, UUID operatorId);

    BatchResponse updateStatus(UUID id, BatchStatus newStatus);

    List<BatchResponse> listChildren(UUID parentId);
}
