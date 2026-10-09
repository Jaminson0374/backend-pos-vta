package co.posinvent.application.usecase;

import co.posinvent.application.port.in.ProductFormulaPort;
import co.posinvent.domain.model.ProductFormula;
import co.posinvent.domain.repository.ProductFormulaRepository;
import co.posinvent.domain.repository.ProductRepository;
import co.posinvent.domain.service.BomExploder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ProductFormulaUseCase implements ProductFormulaPort {

    private final ProductFormulaRepository formulaRepo;
    private final ProductRepository productRepo;
    private final BomExploder bomExploder;

    public ProductFormulaUseCase(ProductFormulaRepository formulaRepo, ProductRepository productRepo,
                                  BomExploder bomExploder) {
        this.formulaRepo = formulaRepo;
        this.productRepo = productRepo;
        this.bomExploder = bomExploder;
    }

    public List<ProductFormula> list(UUID productId) {
        return formulaRepo.findByParentProductId(productId);
    }

    @Transactional
    public ProductFormula add(UUID productId, UUID componentProductId, java.math.BigDecimal quantity,
                               UUID unitOfMeasureId, int sequenceNumber, String notes) {
        if (productId.equals(componentProductId)) {
            throw new IllegalArgumentException("Un producto no puede ser componente de sí mismo");
        }

        if (bomExploder.wouldCreateCycle(productId, componentProductId)) {
            throw new IllegalArgumentException(
                    "Ciclo detectado en BOM: el producto " + componentProductId
                            + " ya es ancestro de " + productId);
        }

        var formula = new ProductFormula(
                null, productId, componentProductId, quantity, unitOfMeasureId,
                sequenceNumber, notes, true, null, null
        );
        return formulaRepo.save(formula);
    }

    @Transactional
    public ProductFormula update(UUID id, java.math.BigDecimal quantity, UUID unitOfMeasureId,
                                  int sequenceNumber, String notes) {
        var existing = formulaRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Componente de fórmula no encontrado"));

        var updated = new ProductFormula(
                id, existing.parentProductId(), existing.componentProductId(),
                quantity, unitOfMeasureId, sequenceNumber, notes, existing.active(),
                existing.createdAt(), null
        );
        return formulaRepo.save(updated);
    }

    @Transactional
    public void remove(UUID id) {
        formulaRepo.delete(id);
    }
}
