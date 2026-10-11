package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
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

    /** Overrides a product's sale price and tax type (pricing used by POS checkout tests). */
    public static void setSalePriceAndTaxType(EntityManager em, UUID productId,
                                              BigDecimal salePrice, String taxType) {
        var p = em.find(ProductEntity.class, productId);
        p.setSalePrice(salePrice);
        p.setTaxType(taxType);
        em.merge(p);
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

    /**
     * Creates a CLIENT third-party with no price list, so the price engine falls
     * back to the product sale price (Tier 3). Used by POS checkout tests.
     */
    public static UUID createClientThirdParty(EntityManager em, String numIdentification, String name) {
        var t = new ThirdPartyEntity();
        t.setNumIdentification(numIdentification);
        t.setName(name);
        t.setType(ThirdParty.ThirdPartyType.CLIENT);
        t.setPersonType(ThirdParty.PersonType.JURIDICA);
        t.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
        t.setTaxResponsibilities(List.of());
        t.setActive(true);
        t.setCreditLimit(BigDecimal.ZERO);
        t.setCurrentBalance(BigDecimal.ZERO);
        em.persist(t);
        return t.getId();
    }

    /**
     * Persists a CONFIRMED ORDER document with a single line for the given
     * product/batch, ready to be invoiced by the POS checkout.
     */
    public static UUID createConfirmedOrder(EntityManager em, String documentNumber,
                                            UUID clientId, UUID warehouseId,
                                            UUID productId, UUID batchId, BigDecimal quantity) {
        var doc = new SalesDocumentEntity();
        doc.setType(SalesDocumentType.ORDER);
        doc.setStatus(SalesDocumentStatus.CONFIRMED);
        doc.setDocumentNumber(documentNumber);
        doc.setClientId(clientId);
        doc.setWarehouseId(warehouseId);
        doc.setTotalNet(BigDecimal.ZERO);
        doc.setTotalTax0(BigDecimal.ZERO);
        doc.setTotalTax5(BigDecimal.ZERO);
        doc.setTotalTax8(BigDecimal.ZERO);
        doc.setTotalTax19(BigDecimal.ZERO);
        doc.setTotalAmount(BigDecimal.ZERO);

        var item = new SaleItemEntity();
        item.setDocument(doc);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setUnitPrice(BigDecimal.ZERO);
        item.setTaxType("EXENTO");
        item.setTaxRate(BigDecimal.ZERO);
        item.setTaxAmount(BigDecimal.ZERO);
        item.setSubtotal(BigDecimal.ZERO);
        item.setLineNumber(1);
        item.setBatchId(batchId);
        doc.getItems().add(item);

        em.persist(doc);
        return doc.getId();
    }
}
