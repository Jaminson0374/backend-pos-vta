package co.posinvent.application.port.in;

import co.posinvent.application.dto.StockResponse;

import java.util.List;
import java.util.UUID;

public interface StockQueryPort {

    List<StockResponse> getByWarehouse(UUID warehouseId);

    List<StockResponse> getByProduct(UUID productId);

    List<StockResponse> getByBatch(UUID batchId);
}
