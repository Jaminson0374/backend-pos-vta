package co.posinvent.application.usecase;

import co.posinvent.application.dto.StockResponse;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StockQueryUseCase {

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