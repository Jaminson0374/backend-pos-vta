package co.posinvent.domain.service;

import co.posinvent.domain.model.ProductFormula;
import co.posinvent.domain.repository.ProductFormulaRepository;
import co.posinvent.domain.repository.StockRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class BomExploder {

    private static final int MAX_DEPTH = 5;

    private final ProductFormulaRepository formulaRepo;
    private final StockRepository stockRepository;

    public BomExploder(ProductFormulaRepository formulaRepo, StockRepository stockRepository) {
        this.formulaRepo = formulaRepo;
        this.stockRepository = stockRepository;
    }

    public record ExplodedComponent(UUID productId, BigDecimal totalQuantity, int depth) {}

    /**
     * Explodes a formula recursively, returning the components that must be consumed.
     *
     * <p>A manufacturable component that already has available stock in the warehouse is treated
     * as a leaf: it is consumed as an intermediate, enabling the "secondary production chain"
     * (openspec produccion S2 — FefoPicker finds the prior stock of the intermediate). A
     * manufacturable component without stock is exploded further down to its raw materials.
     * Non-manufacturable components are always leaves.</p>
     *
     * @param formulaProductId the formula product to explode
     * @param warehouseId the warehouse whose available stock decides intermediate vs. recursion
     * @param quantity the quantity of formula product to produce
     * @return the components (raw materials and/or stocked intermediates) to consume
     */
    public List<ExplodedComponent> explode(UUID formulaProductId, UUID warehouseId, BigDecimal quantity) {
        var result = new ArrayList<ExplodedComponent>();
        explodeRecursive(formulaProductId, warehouseId, quantity, 0, result);
        return result;
    }

    private void explodeRecursive(UUID formulaProductId, UUID warehouseId, BigDecimal quantity, int depth,
                                   List<ExplodedComponent> result) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException(
                    "Profundidad máxima de BOM excedida (" + MAX_DEPTH + " niveles) para producto " + formulaProductId);
        }

        var components = formulaRepo.findByParentProductId(formulaProductId).stream()
                .filter(ProductFormula::active)
                .toList();

        if (components.isEmpty()) {
            throw new IllegalArgumentException(
                    "El producto " + formulaProductId + " no tiene fórmula definida y no puede ser producido");
        }

        for (var comp : components) {
            BigDecimal neededQty = comp.quantity().multiply(quantity);
            boolean hasOwnFormula = !formulaRepo.findByParentProductId(comp.componentProductId()).isEmpty();

            if (hasOwnFormula && !hasAvailableStock(comp.componentProductId(), warehouseId)) {
                explodeRecursive(comp.componentProductId(), warehouseId, neededQty, depth + 1, result);
            } else {
                result.add(new ExplodedComponent(comp.componentProductId(), neededQty, depth));
            }
        }
    }

    private boolean hasAvailableStock(UUID productId, UUID warehouseId) {
        return stockRepository.findAvailableByProductWarehouse(productId, warehouseId).stream()
                .anyMatch(s -> s.availableQuantity().compareTo(BigDecimal.ZERO) > 0);
    }

    /**
     * Detecta si agregar newComponentId como componente de formulaId crearía un ciclo.
     * DFS desde newComponentId hacia arriba en el BOM.
     */
    public boolean wouldCreateCycle(UUID formulaId, UUID newComponentId) {
        var visited = new HashSet<UUID>();
        return dfsAncestors(newComponentId, formulaId, visited);
    }

    private boolean dfsAncestors(UUID current, UUID target, Set<UUID> visited) {
        if (current.equals(target)) return true;
        if (!visited.add(current)) return false;

        var parents = formulaRepo.findAllByComponentProductId(current);
        for (var parent : parents) {
            if (dfsAncestors(parent.parentProductId(), target, visited)) return true;
        }
        return false;
    }
}
