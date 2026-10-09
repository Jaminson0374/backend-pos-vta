package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.Warehouse;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class TestDataFactory {

    private TestDataFactory() {}

    public static UUID createWarehouse(EntityManager em, String name, Warehouse.WarehouseType type) {
        var wh = new WarehouseEntity();
        wh.setName(name);
        wh.setWarehouseType(type);
        wh.setActive(true);
        em.persist(wh);
        return wh.getId();
    }

    public static UUID createProduct(EntityManager em, String codePrefix, String name,
                                      boolean manufacturedInHouse, boolean inventoriable) {
        var p = new ProductEntity();
        p.setProductCode(codePrefix + "-" + UUID.randomUUID().toString().substring(0, 6));
        p.setName(name);
        p.setManufacturedInHouse(manufacturedInHouse);
        p.setInventoriable(inventoriable);
        p.setActive(true);
        p.setCostPrice(BigDecimal.ZERO);
        p.setProfitMargin(BigDecimal.ZERO);
        p.setTaxType("EXENTO");
        p.setSalePrice(BigDecimal.ZERO);
        p.setCostingMethod("PROMEDIO_PONDERADO");
        p.setInitialStock(BigDecimal.ZERO);
        p.setMinStock(BigDecimal.ZERO);
        p.setMaxStock(BigDecimal.ZERO);
        p.setTotalStock(BigDecimal.ZERO);
        em.persist(p);
        return p.getId();
    }

    public static UUID createBatch(EntityManager em, UUID productId, UUID supplierId, UUID warehouseId,
                                    BigDecimal weight, Batch.BatchStatus status) {
        var b = new BatchEntity();
        b.setProductId(productId);
        b.setSupplierId(supplierId);
        b.setWarehouseId(warehouseId);
        b.setEntryDate(LocalDate.now());
        b.setInitialWeight(weight);
        b.setPurchaseCost(BigDecimal.ZERO);
        b.setStatus(status);
        b.setNotes("Batch de test");
        em.persist(b);
        return b.getId();
    }

    public static void createStock(EntityManager em, UUID productId, UUID batchId,
                                    UUID warehouseId, BigDecimal quantity, BigDecimal unitCost) {
        var s = new InventoryStockEntity();
        s.setProductId(productId);
        s.setBatchId(batchId);
        s.setWarehouseId(warehouseId);
        s.setCurrentQuantity(quantity);
        s.setCommittedQuantity(BigDecimal.ZERO);
        s.setUnitCost(unitCost);
        em.persist(s);
    }

    public static void createFormula(EntityManager em, UUID parentProductId,
                                      UUID componentProductId, BigDecimal quantity) {
        var f = new ProductFormulaEntity();
        f.setParentProductId(parentProductId);
        f.setComponentProductId(componentProductId);
        f.setQuantity(quantity);
        f.setSequenceNumber(1);
        f.setActive(true);
        em.persist(f);
    }

    public static void makePerishable(EntityManager em, UUID productId) {
        var p = em.find(ProductEntity.class, productId);
        p.setPerishable(true);
        em.merge(p);
    }

    public static void closeBatch(EntityManager em, UUID batchId) {
        var b = em.find(BatchEntity.class, batchId);
        b.setStatus(Batch.BatchStatus.CLOSED);
        em.merge(b);
    }
}
