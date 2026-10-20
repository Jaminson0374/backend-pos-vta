package co.posinvent.infrastructure.adapters.out.persistence;

import co.posinvent.domain.model.Batch;
import co.posinvent.domain.model.SalesDocumentStatus;
import co.posinvent.domain.model.SalesDocumentType;
import co.posinvent.domain.model.ShiftStatus;
import co.posinvent.domain.model.ThirdParty;
import co.posinvent.domain.model.Warehouse;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

    public static UUID createCashRegister(EntityManager em, String name) {
        var register = new CashRegisterEntity();
        register.setName(name);
        register.setActive(true);
        register.setCreatedAt(OffsetDateTime.now());
        em.persist(register);
        return register.getId();
    }

    /**
     * Persists an OPEN shift for {@code userId} on the given register. The partial unique
     * index allows only one OPEN shift per register, so each test must create its own
     * register (or reuse the returned shift).
     */
    public static UUID createOpenShift(EntityManager em, UUID userId, UUID cashRegisterId) {
        var shift = new ShiftEntity();
        shift.setCashRegisterId(cashRegisterId);
        shift.setUserId(userId);
        shift.setOpeningTime(OffsetDateTime.now().minusHours(1));
        shift.setOpeningAmount(BigDecimal.ZERO);
        shift.setStatus(ShiftStatus.OPEN);
        em.persist(shift);
        return shift.getId();
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
        return createClientThirdParty(em, numIdentification, name, BigDecimal.ZERO);
    }

    /** Creates a CLIENT third-party with the given credit limit. */
    public static UUID createClientThirdParty(EntityManager em, String numIdentification, String name,
                                              BigDecimal creditLimit) {
        var t = new ThirdPartyEntity();
        t.setNumIdentification(numIdentification);
        t.setName(name);
        t.setType(ThirdParty.ThirdPartyType.CLIENT);
        t.setPersonType(ThirdParty.PersonType.JURIDICA);
        t.setTaxRegime(ThirdParty.TaxRegime.ORDINARIO);
        t.setTaxResponsibilities(List.of());
        t.setActive(true);
        t.setCreditLimit(creditLimit);
        em.persist(t);
        return t.getId();
    }

    /**
     * Persists a sales-document anchor and the AR row that references it. The anchor only satisfies
     * the {@code accounts_receivable.document_id} foreign key; the AR row is what the derived
     * customer balance sums.
     */
    public static UUID createAccountsReceivable(EntityManager em, UUID clientId, UUID warehouseId,
                                                 String documentNumber, BigDecimal outstanding) {
        var doc = new SalesDocumentEntity();
        doc.setType(SalesDocumentType.ORDER);
        doc.setStatus(SalesDocumentStatus.CONFIRMED);
        doc.setDocumentNumber(documentNumber);
        doc.setClientId(clientId);
        doc.setWarehouseId(warehouseId);
        doc.setTotalNet(BigDecimal.ZERO);
        doc.setTotalTax0(BigDecimal.ZERO);
        doc.setTotalTax5(BigDecimal.ZERO);
        doc.setTotalTax19(BigDecimal.ZERO);
        doc.setTotalAmount(BigDecimal.ZERO);
        em.persist(doc);

        var ar = new AccountsReceivableEntity();
        ar.setClientId(clientId);
        ar.setDocumentId(doc.getId());
        ar.setTotalAmount(outstanding);
        ar.setPaidAmount(BigDecimal.ZERO);
        ar.setOutstanding(outstanding);
        ar.setDueDate(LocalDate.now().plusDays(30));
        ar.setStatus("OPEN");
        em.persist(ar);
        return ar.getId();
    }

    /**
     * Persists a CONFIRMED ORDER document with a single line for the given
     * product/batch, ready to be invoiced by the POS checkout.
     */
    public static UUID createConfirmedOrder(EntityManager em, String documentNumber,
                                            UUID clientId, UUID warehouseId,
                                            UUID productId, UUID batchId, BigDecimal quantity) {
        return createConfirmedOrder(em, documentNumber, clientId, warehouseId,
                List.of(new OrderLine(productId, batchId, quantity)));
    }

    /**
     * Persists a CONFIRMED ORDER document with one line per supplied {@link OrderLine},
     * ready to be invoiced by the POS checkout. Used to exercise multiple tax rates in
     * a single invoice.
     */
    public static UUID createConfirmedOrder(EntityManager em, String documentNumber,
                                            UUID clientId, UUID warehouseId,
                                            List<OrderLine> lines) {
        var doc = new SalesDocumentEntity();
        doc.setType(SalesDocumentType.ORDER);
        doc.setStatus(SalesDocumentStatus.CONFIRMED);
        doc.setDocumentNumber(documentNumber);
        doc.setClientId(clientId);
        doc.setWarehouseId(warehouseId);
        doc.setTotalNet(BigDecimal.ZERO);
        doc.setTotalTax0(BigDecimal.ZERO);
        doc.setTotalTax5(BigDecimal.ZERO);
        doc.setTotalTax19(BigDecimal.ZERO);
        doc.setTotalAmount(BigDecimal.ZERO);

        int lineNumber = 1;
        for (var line : lines) {
            var item = new SaleItemEntity();
            item.setDocument(doc);
            item.setProductId(line.productId());
            item.setQuantity(line.quantity());
            item.setUnitPrice(BigDecimal.ZERO);
            item.setTaxType("EXENTO");
            item.setTaxRate(BigDecimal.ZERO);
            item.setTaxAmount(BigDecimal.ZERO);
            item.setSubtotal(BigDecimal.ZERO);
            item.setLineNumber(lineNumber++);
            item.setBatchId(line.batchId());
            item.setDiscountType(line.discountType());
            item.setDiscountValue(line.discountValue());
            doc.getItems().add(item);
        }

        em.persist(doc);
        return doc.getId();
    }

    /** A product/batch/quantity tuple used to seed a CONFIRMED order line. */
    public record OrderLine(
            UUID productId,
            UUID batchId,
            BigDecimal quantity,
            String discountType,
            BigDecimal discountValue
    ) {
        public OrderLine(UUID productId, UUID batchId, BigDecimal quantity) {
            this(productId, batchId, quantity, "NONE", BigDecimal.ZERO);
        }
    }

    /**
     * Persists an ACTIVE DIAN resolution covering {@code [validFrom, validTo]} and the
     * numeric range {@code [rangeFrom, rangeTo]}, so POS checkout can issue fiscal
     * numbers. Without an active resolution the checkout now fails with
     * {@code DIAN_NO_ACTIVE_RESOLUTION}.
     */
    public static UUID createActiveDianResolution(EntityManager em, String prefix,
                                                  long rangeFrom, long rangeTo,
                                                  LocalDate validFrom, LocalDate validTo) {
        // Integration tests share one PostgreSQL container, so a previous test
        // class may have left an active resolution behind. Deactivate all so the
        // seeded resolution is deterministically the only active one.
        em.createQuery("UPDATE DianResolutionEntity r SET r.active = false").executeUpdate();

        var r = new DianResolutionEntity();
        r.setResolutionNumber("RES-" + UUID.randomUUID().toString().substring(0, 8));
        r.setResolutionDate(LocalDate.now());
        r.setValidFrom(validFrom);
        r.setValidTo(validTo);
        r.setPrefix(prefix);
        r.setRangeFrom(rangeFrom);
        r.setRangeTo(rangeTo);
        r.setSoftwarePin("PIN");
        r.setActive(true);
        em.persist(r);
        return r.getId();
    }
}
