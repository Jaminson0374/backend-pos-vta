package co.posinvent.application.usecase;

import co.posinvent.application.dto.AvailableBatchResponse;
import co.posinvent.application.dto.StockResponse;
import co.posinvent.application.port.in.StockQueryPort;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StockQueryUseCase implements StockQueryPort {

    private final StockRepository stockRepository;
    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;

    public StockQueryUseCase(
            StockRepository stockRepository,
            ProductRepository productRepository,
            BatchRepository batchRepository
    ) {
        this.stockRepository = stockRepository;
        this.productRepository = productRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getByWarehouse(UUID warehouseId) {
        var stocks = stockRepository.findByWarehouse(warehouseId);
        return enrich(stocks);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getByProduct(UUID productId) {
        var stocks = stockRepository.findByProduct(productId);
        return enrich(stocks);
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getByBatch(UUID batchId) {
        var stocks = stockRepository.findByBatch(batchId);
        return enrich(stocks);
    }

    /**
     * Lists the batches that can still be reserved for a product in a warehouse, in
     * FEFO order (earliest expiration first). Only rows with a strictly positive
     * available quantity ({@code current - committed}) are returned; the ordering is
     * provided by the repository query and preserved by the stream mapping.
     */
    @Transactional(readOnly = true)
    @Override
    public List<AvailableBatchResponse> getAvailableBatches(UUID productId, UUID warehouseId) {
        var stocks = stockRepository.findReservableByProductWarehouse(productId, warehouseId);
        if (stocks.isEmpty()) return List.of();

        return stocks.stream()
                .filter(s -> s.availableQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(s -> new AvailableBatchResponse(
                        s.batchId(),
                        batchRepository.findById(s.batchId())
                                .map(Batch::expirationDate)
                                .orElse(null),
                        s.availableQuantity()))
                .toList();
    }

    private List<StockResponse> enrich(List<InventoryStock> stocks) {
        if (stocks.isEmpty()) return List.of();

        // Batch-load all referenced products and batches
        var productIds = new HashSet<>(stocks.stream().map(InventoryStock::productId).toList());
        var batchIds = new HashSet<>(stocks.stream().map(InventoryStock::batchId).toList());

        var productMap = productIds.stream()
                .map(productRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(
                        p -> p.id(),
                        p -> p,
                        (a, b) -> a
                ));

        var batchMap = batchIds.stream()
                .map(batchRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(
                        b -> b.id(),
                        b -> b,
                        (a, b) -> a
                ));

        return stocks.stream()
                .map(s -> StockResponse.enriched(
                        s,
                        productMap.get(s.productId()),
                        batchMap.get(s.batchId())
                ))
                .toList();
    }
}