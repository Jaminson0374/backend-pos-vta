package co.posinvent.application.usecase;

import co.posinvent.application.dto.SlaughterRequest;
import co.posinvent.application.dto.SlaughterResponse;
import co.posinvent.application.port.in.ProcessSlaughterPort;
import co.posinvent.domain.exception.BusinessException;
import co.posinvent.domain.exception.ResourceNotFoundException;
import co.posinvent.domain.model.Animal;
import co.posinvent.domain.model.Animal.AnimalStatus;
import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Batch.BatchStatus;
import co.posinvent.domain.model.BatchType;
import co.posinvent.domain.model.InventoryStock;
import co.posinvent.domain.model.MovementType;
import co.posinvent.domain.model.Slaughter;
import co.posinvent.domain.model.Warehouse.WarehouseType;
import co.posinvent.domain.repository.AnimalRepository;
import co.posinvent.domain.repository.BatchRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.repository.SlaughterRepository;
import co.posinvent.domain.repository.StockRepository;
import co.posinvent.domain.repository.ThirdPartyRepository;
import co.posinvent.domain.repository.WarehouseRepository;
import co.posinvent.domain.service.SlaughterDomainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ProcessSlaughterUseCase implements ProcessSlaughterPort {

    private final AnimalRepository animalRepository;
    private final SlaughterRepository slaughterRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final BatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final ThirdPartyRepository thirdPartyRepository;
    private final SlaughterDomainService domainService;
    private final RecordMovementUseCase recordMovement;
    private final OptimisticConcurrencyExecutor concurrencyExecutor;

    public ProcessSlaughterUseCase(
            AnimalRepository animalRepository,
            BatchRepository batchRepository,
            ProductRepository productRepository,
            ThirdPartyRepository thirdPartyRepository,
            WarehouseRepository warehouseRepository,
            SlaughterRepository slaughterRepository,
            StockRepository stockRepository,
            SlaughterDomainService domainService,
            RecordMovementUseCase recordMovement,
            OptimisticConcurrencyExecutor concurrencyExecutor
    ) {
        this.animalRepository = animalRepository;
        this.batchRepository = batchRepository;
        this.productRepository = productRepository;
        this.thirdPartyRepository = thirdPartyRepository;
        this.warehouseRepository = warehouseRepository;
        this.slaughterRepository = slaughterRepository;
        this.stockRepository = stockRepository;
        this.domainService = domainService;
        this.recordMovement = recordMovement;
        this.concurrencyExecutor = concurrencyExecutor;
    }

    public SlaughterResponse process(SlaughterRequest request, UUID operatorId) {
        return concurrencyExecutor.execute(() -> {
            // 1. Validar que el animal exista y esté en estado RECEIVED
            var animal = animalRepository.findById(request.animalId())
                    .orElseThrow(() -> new ResourceNotFoundException("Animal", request.animalId()));

            // 2. Validación de dominio: sourceType, carcassWeight, purchaseCost, justification
            var yieldPercentage = domainService.validate(
                    animal,
                    request.carcassWeight(),
                    request.purchaseCost(),
                    request.sourceType(),
                    request.justification()
            );

            // 3. Validar que el inspector exista
            thirdPartyRepository.findById(request.inspectorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Inspector", request.inspectorId()));

            // 4. Buscar el producto CANAL (materia prima resultante)
            var canalProduct = productRepository.findByProductCode("CANAL")
                    .orElseThrow(() -> new BusinessException(
                            "CANAL_PRODUCT_NOT_FOUND",
                            "No se encontró el producto CANAL. Ejecutá la migración V23 o creá el producto con código 'CANAL'."
                    ));

            // 5. Buscar la primera bodega CANAL activa
            var canalWarehouse = warehouseRepository.findFirstActiveByType(WarehouseType.CANAL)
                    .orElseThrow(() -> new BusinessException(
                            "CANAL_WAREHOUSE_NOT_FOUND",
                            "No hay una bodega de tipo CANAL activa. Creá una bodega CANAL antes de ejecutar la faena."
                    ));

            // 6. Crear el lote (Batch) en la bodega CANAL
            var batch = batchRepository.save(new Batch(
                    null,                           // id
                    canalProduct.id(),              // productId — producto CANAL
                    animal.supplierId(),            // supplierId — heredado del animal
                    canalWarehouse.id(),            // warehouseId
                    request.slaughterDate(),        // entryDate
                    request.carcassWeight(),        // initialWeight
                    request.purchaseCost(),         // purchaseCost
                    BatchStatus.OPEN,               // status
                    request.notes(),                // notes
                    null,                           // expirationDate
                    operatorId,                     // createdBy
                    null,
                    null,
                    null,                           // updatedBy
                    null,                           // sourceReceiptId — no aplica para faena directa
                    null,                           // ocId — no aplica para faena directa
                    null, null, null,               // productName, supplierName, warehouseName
                    null,                           // parentBatchId
                    BatchType.STANDARD,             // batchType
                    null                            // unitOfMeasureId
            ));

            // 7. Upsert de stock para el producto CANAL con el nuevo lote
            var existingStock = stockRepository.findByProductBatchWarehouse(
                    canalProduct.id(), batch.id(), canalWarehouse.id());

            var unitCost = request.purchaseCost().divide(
                    request.carcassWeight(), 6, java.math.RoundingMode.HALF_UP);
            var previousQty = BigDecimal.ZERO;

            if (existingStock.isPresent()) {
                var stock = existingStock.get();
                previousQty = stock.currentQuantity();
                stockRepository.save(new InventoryStock(
                        stock.id(),
                        stock.productId(),
                        stock.batchId(),
                        stock.warehouseId(),
                        request.carcassWeight(),
                        BigDecimal.ZERO,
                        unitCost,
                        stock.createdAt(),
                        null
                ));
            } else {
                stockRepository.save(new InventoryStock(
                        null,
                        canalProduct.id(),
                        batch.id(),
                        canalWarehouse.id(),
                        request.carcassWeight(),
                        BigDecimal.ZERO,
                        unitCost,
                        null,
                        null
                ));
            }

            productRepository.recalculateTotalStock(canalProduct.id());
            recordMovement.record(
                    canalProduct.id(), batch.id(), canalWarehouse.id(),
                    MovementType.ENTRY,
                    request.carcassWeight(), unitCost,
                    previousQty, request.carcassWeight(),
                    "SLAUGHTER", batch.id(),
                    "Faena — animal #" + animal.id()
            );

            // 8. Persistir el registro de faena
            var slaughter = slaughterRepository.save(new Slaughter(
                    null,
                    animal.id(),
                    request.carcassWeight(),
                    yieldPercentage,
                    request.slaughterDate(),
                    request.invimaPlant(),
                    request.inspectorId(),
                    request.sourceType(),
                    request.justification(),
                    request.purchaseCost(),
                    batch.id(),
                    request.notes(),
                    operatorId,
                    null,
                    null
            ));

            // 9. Marcar el animal como SLAUGHTERED
            animalRepository.save(new Animal(
                    animal.id(),
                    animal.icaLotNumber(),
                    animal.supplierId(),
                    animal.species(),
                    animal.liveWeight(),
                    animal.receptionDate(),
                    AnimalStatus.SLAUGHTERED,
                    animal.notes(),
                    animal.createdBy(),
                    animal.createdAt(),
                    null
            ));

            return SlaughterResponse.from(slaughter);
        });
    }
}
