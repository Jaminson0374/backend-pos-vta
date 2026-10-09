package co.posinvent.infrastructure.adapters.in.rest;

import co.posinvent.application.dto.ManualStockEntryRequest;
import co.posinvent.application.dto.ManualStockExitRequest;
import co.posinvent.application.dto.StockResponse;
import co.posinvent.application.port.in.ManualStockEntryPort;
import co.posinvent.application.port.in.ManualStockExitPort;
import co.posinvent.application.port.in.StockQueryPort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock")
public class StockController {

    private final StockQueryPort stockQueryUseCase;
    private final ManualStockEntryPort manualEntry;
    private final ManualStockExitPort manualExit;

    public StockController(
            StockQueryPort stockQueryUseCase,
            ManualStockEntryPort manualEntry,
            ManualStockExitPort manualExit
    ) {
        this.stockQueryUseCase = stockQueryUseCase;
        this.manualEntry = manualEntry;
        this.manualExit = manualExit;
    }

    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<List<StockResponse>> byWarehouse(@PathVariable UUID warehouseId) {
        return ResponseEntity.ok(stockQueryUseCase.getByWarehouse(warehouseId));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<List<StockResponse>> byProduct(@PathVariable UUID productId) {
        return ResponseEntity.ok(stockQueryUseCase.getByProduct(productId));
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<List<StockResponse>> byBatch(@PathVariable UUID batchId) {
        return ResponseEntity.ok(stockQueryUseCase.getByBatch(batchId));
    }

    @PostMapping("/entry")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<Void> manualEntry(@RequestBody ManualStockEntryRequest request) {
        manualEntry.execute(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/exit")
    @PreAuthorize("hasAnyRole('ADMIN','ALMACENISTA')")
    public ResponseEntity<Void> manualExit(@RequestBody ManualStockExitRequest request) {
        manualExit.execute(request);
        return ResponseEntity.ok().build();
    }
}
